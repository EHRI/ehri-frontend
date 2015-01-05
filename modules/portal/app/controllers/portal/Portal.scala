package controllers.portal

import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import views.html.p
import utils.search._
import play.api.cache.Cached
import defines.EntityType
import play.api.libs.ws.WS
import play.twirl.api.Html
import solr.SolrConstants
import backend.Backend
import controllers.base.SessionPreferences
import utils._

import com.google.inject._
import views.html.errors.pageNotFound
import org.joda.time.DateTime
import caching.FutureCache
import controllers.portal.base.PortalController
import scala.concurrent.Future
import backend.rest.cypher.CypherDAO
import backend.rest.SearchDAO
import play.api.libs.json.{Json, JsString}

@Singleton
case class Portal @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO)
  extends PortalController
  with Search
  with FacetConfig
  with SessionPreferences[SessionPrefs]
  with Secured {

  val defaultPreferences = new SessionPrefs

  private val portalRoutes = controllers.portal.routes.Portal

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private val defaultSearchTypes = List(
    EntityType.Repository,
    EntityType.DocumentaryUnit,
    EntityType.HistoricalAgent,
    EntityType.Country
  )

  def personalisedActivity = WithUserAction.async{ implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
    val listParams = RangeParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    backend.listEventsForUser[SystemEvent](request.profile.id, listParams, eventFilter).map { events =>
      if (isAjax) Ok(p.activity.eventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(p.activity.activity(events, listParams))
    }
  }

  def search = UserBrowseAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(
        entities = defaultSearchTypes, sort = Some(SearchOrder.Score)),
      facetBuilder = globalSearchFacets, mode = SearchMode.DefaultNone
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.search(page, params, facets, portalRoutes.search(), request.watched))
    }
  }


  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @return
   */
  def filter = filterAction() { page => implicit userOpt => implicit request =>
    Ok(Json.obj(
      "numPages" -> page.numPages,
      "page" -> page.page,
      "items" -> page.items
    ))
  }

  /**
   * Portal index. Currently this just shows an overview of the data
   * extracted from the search engine facet counts for different
   * types.
   */
  def index = OptionalUserAction.async { implicit request =>
    FutureCache.getOrElse("index:metrics", 60 * 5) {
      find[AnyModel](
        defaultParams = SearchParams(
          // we don't need results here because we're only using the facets
          count = Some(0),
          entities = defaultSearchTypes
        ),
        facetBuilder = entityMetrics
      ).map(_.page.facets)
    }.map(facets => Ok(p.portal(Stats(facets))))
  }

  def browseItem(entityType: EntityType.Value, id: String) = Action { implicit request =>
    entityType match {
      case EntityType.DocumentaryUnit => Redirect(controllers.portal.routes.DocumentaryUnits.browse(id))
      case EntityType.Repository => Redirect(controllers.portal.routes.Repositories.browse(id))
      case EntityType.HistoricalAgent => Redirect(controllers.portal.routes.HistoricalAgents.browse(id))
      case EntityType.Concept => Redirect(portalRoutes.browseConcept(id))
      case EntityType.Country => Redirect(portalRoutes.browseCountry(id))
      case EntityType.Link => Redirect(portalRoutes.browseLink(id))
      case EntityType.Annotation => Redirect(portalRoutes.browseAnnotation(id))
      case EntityType.Vocabulary => Redirect(portalRoutes.browseVocabulary(id))
      case EntityType.UserProfile => Redirect(controllers.portal.social.routes.Social.browseUser(id))
      case EntityType.Group => Redirect(portalRoutes.browseGroup(id))
      case _ => NotFound(controllers.renderError("errors.pageNotFound", pageNotFound()))
    }
  }

  def browseCountries = UserBrowseAction.async { implicit request =>
    find[Country](entities = List(EntityType.Country),
      facetBuilder = countryFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.country.list(page, params, facets, portalRoutes.browseCountries(), request.watched))
    }
  }

  def browseCountry(id: String) = getItemAction[Country](EntityType.Country, id) {
      item => details => implicit userOpt => implicit request =>
    if (isAjax) Ok(p.country.itemDetails(item, details.annotations, details.links, details.watched))
    else Ok(p.country.show(item, details.annotations, details.links, details.watched))
  }

  def searchCountry(id: String) = getItemAction.async[Country](EntityType.Country, id) {
      item => details => implicit userOpt => implicit request =>
    find[Repository](
      filters = Map("countryCode" -> item.id),
      facetBuilder = repositorySearchFacets,
      entities = List(EntityType.Repository)
    ).map { case QueryResult(page, params, facets) =>
      if (isAjax) Ok(p.country.childItemSearch(item, page, params, facets,
          portalRoutes.searchCountry(id), details.watched))
      else Ok(p.country.search(item, page, params, facets,
          portalRoutes.searchCountry(id), details.watched))
    }
  }

  def browseLink(id: String) = getItemAction[Link](EntityType.Link, id) {
      item => details => implicit userOpt => implicit request =>
    Ok(p.link.show(item))
  }

  def browseGroup(id: String) = getItemAction[Group](EntityType.Group, id) {
      item => details => implicit userOpt => implicit request =>
    Ok(p.group.show(item))
  }

  def browseAnnotations = OptionalUserAction.async { implicit request =>
    find[Annotation](
      entities = List(EntityType.Annotation),
      facetBuilder = annotationFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.annotation.list(page, params, facets, portalRoutes.browseAnnotations()))
    }
  }

  def browseAnnotation(id: String) = getItemAction[Annotation](EntityType.Annotation, id) {
      ann => details => implicit userOpt => implicit request =>
    Ok(p.annotation.show(ann))
  }

  def browseVocabulary(id: String) = getItemAction[Vocabulary](EntityType.Vocabulary, id) {
    item => details => implicit userOpt => implicit request =>
      if (isAjax) Ok(p.vocabulary.itemDetails(item, details.annotations, details.links, details.watched))
      else Ok(p.vocabulary.show(item, details.annotations, details.links, details.watched))
  }

  def searchVocabulary(id: String) = getItemAction.async[Vocabulary](EntityType.Vocabulary, id) {
      item => details => implicit userOpt => implicit request =>
    val filters = Map(SolrConstants.HOLDER_ID -> item.id, SolrConstants.TOP_LEVEL -> true.toString)
    find[Concept](
      filters = filters,
      entities = List(EntityType.Concept),
      facetBuilder = conceptFacets
    ).map { case QueryResult(page, params, facets) =>
      if (isAjax) Ok(p.vocabulary.childItemSearch(item, page, params, facets,
        portalRoutes.searchVocabulary(id), details.watched))
      else Ok(p.vocabulary.search(item, page, params, facets,
        portalRoutes.searchVocabulary(id), details.watched))
    }
  }


  def browseConcept(id: String) = getItemAction.async[Concept](EntityType.Concept, id) {
      item => details => implicit userOpt => implicit request =>
    find[Concept](
      filters = Map("parentId" -> item.id),
      facetBuilder = conceptFacets,
      entities = List(EntityType.Concept)
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.concept.show(item, page, params, facets,
        portalRoutes.browseConcept(id), details.annotations, details.links, details.watched))
    }
  }

  def browseConcepts = UserBrowseAction.async { implicit request =>
    find[Concept](
      entities = List(EntityType.Concept),
      facetBuilder = conceptFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.concept.list(page, params, facets, portalRoutes.browseConcepts(),
        request.watched))
    }
  }

  def itemHistory(id: String, modal: Boolean = false) = OptionalUserAction.async { implicit request =>
    val params: RangeParams = RangeParams.fromRequest(request)
    val filters = SystemEventParams.fromRequest(request)
    backend.history[SystemEvent](id, params, filters).map { events =>
      if (isAjax && modal) Ok(p.activity.itemActivityModal(events))
      else if (isAjax) Ok(p.activity.itemEventItems(events))
        .withHeaders("activity-more" -> events.more.toString)
      else Ok(p.activity.itemActivity(events, params))
    }
  }

  def placeholder = Cached.status(_ => "pages:portalPlaceholder", OK, 60 * 60) {
    Action { implicit request =>
      Ok(views.html.placeholder())
    }
  }

  def dataPolicy = Cached("pages:portalDataPolicy") {
    OptionalUserAction.apply { implicit request =>
      Ok(p.dataPolicy())
    }
  }

  def terms = OptionalUserAction.apply { implicit request =>
    Ok(p.terms())
  }

  def about = OptionalUserAction.apply { implicit request =>
    Ok(p.about())
  }

  def contact = OptionalUserAction.apply { implicit request =>
    Ok(p.contact())
  }

  case class NewsItem(title: String, link: String, description: Html, pubDate: Option[DateTime] = None)

  final val NUM_NEWS_ITEMS = 2

  object NewsItem {
    import scala.util.control.Exception._
    import org.joda.time.format.DateTimeFormat
    def fromRss(feed: String): Seq[NewsItem] = {
      val pat = DateTimeFormat.forPattern("EEE, dd MMM yyyy H:m:s Z")
      (scala.xml.XML.loadString(feed) \\ "item").take(NUM_NEWS_ITEMS).map { item =>
        NewsItem(
          title = (item \ "title").text,
          link = (item \ "link").text,
          description = Html((item \ "description").text),
          pubDate = allCatch.opt(DateTime.parse((item \ "pubDate").text, pat))
        )
      }
    }
  }

  def newsFeed = Cached.status(_ => "pages.newsFeed", OK, 60 * 60) {
    Action.async { request =>
      WS.url("http://www.ehri-project.eu/rss.xml").get().map { r =>
        Ok(p.newsFeed(NewsItem.fromRss(r.body)))
      }
    }
  }


  val searchLinksForm = Form(
    single(
      "type" -> optional(text.verifying("NoTypeGiven", f => f match {
          case c => EntityType.values.map( v => v.toString).contains(c)
        }))
    )
  )


  def searchLinks(target: String, documentType: String = EntityType.DocumentaryUnit.toString, context: Option[String] = None): Future[Seq[Long]] = {
    
    val cypher = new CypherDAO
    context match {
      case Some(str) => {
        val query =  s"""
        START 
          virtualUnit = node:entities(__ID__= {inContext}), 
          accessPoints = node:entities(__ID__= {accessPoint})
        MATCH 
             (link)-[:inContextOf]->virtualUnit,
            (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
         WHERE doc <> accessPoints AND doc.__ISA__ = {type}
         RETURN ID(doc) LIMIT 5
        """.stripMargin
        val params =  Map(
          "inContext" -> JsString(str),
          "accessPoint" -> JsString(target),
          "type" -> JsString(documentType)
        )
        cypher.cypher(query, params).map { r =>
          (r \ "data").as[Seq[Seq[Long]]].flatten
        }
      }
      case _ => {
        val query : String =  
        s"""
        START 
          accessPoints = node:entities(__ID__= {accessPoint})
        MATCH 
             (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
         WHERE doc <> accessPoints AND doc.__ISA__ = {type}
         RETURN ID(doc) LIMIT 5
        """.stripMargin
        val params =  Map(
          "accessPoint" -> JsString(target),
          "type" -> JsString(documentType)
        )
        cypher.cypher(query, params).map { r =>
          (r \ "data").as[Seq[Seq[Long]]].flatten
        }
      }
    }
  }

  // FIXME: Figure out what this is for!
  def linkedData(id: String) = UserBrowseAction.async { implicit request =>
    for {
      ids <- searchLinksForm.bindFromRequest(request.queryString).fold(
        errs => searchLinks(id), {
            case Some(t) => searchLinks(id, t)
            case _ => searchLinks(id)
        })
      docs <- SearchDAO.listByGid[AnyModel](ids)
    } yield Ok(Json.toJson(docs.zip(ids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }

  // FIXME: Figure out what this is for!
  def linkedDataInContext(id: String, context: String) = UserBrowseAction.async { implicit request =>
    for {
      ids <-  searchLinksForm.bindFromRequest(request.queryString).fold(
        errs => searchLinks(id, context=Some(context)), {
            case Some(t) => searchLinks(id, t, Some(context))
            case _ => searchLinks(id, context=Some(context))
        })
      docs <- SearchDAO.listByGid[AnyModel](ids)
    } yield Ok(Json.toJson(docs.zip(ids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }
}

