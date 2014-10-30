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
import play.api.libs.json.Json
import play.api.cache.Cached
import defines.EntityType
import play.api.libs.ws.WS
import play.twirl.api.Html
import solr.SolrConstants
import backend.Backend
import controllers.base.{SessionPreferences, ControllerHelpers}
import jp.t2v.lab.play2.auth.LoginLogout
import utils._

import com.google.inject._
import views.html.errors.pageNotFound
import org.joda.time.DateTime
import caching.FutureCache

/*
 *    "Linked" Data import
 */
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

import backend.rest.cypher.CypherDAO
import backend.rest.{SearchDAO}
import play.api.libs.json.{Json, JsValue, JsString, JsArray}

@Singleton
case class Portal @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO)
  extends Controller
  with LoginLogout
  with ControllerHelpers
  with Search
  with FacetConfig
  with PortalBase
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val portalRoutes = controllers.portal.routes.Portal

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

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

  def search = userBrowseAction.async { implicit userDetails => implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(
        entities = defaultSearchTypes, sort = Some(SearchOrder.Score)),
      facetBuilder = globalSearchFacets, mode = SearchMode.DefaultNone
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.search(page, params, facets, portalRoutes.search(), userDetails.watchedItems))
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
  def index = userProfileAction.async { implicit userOpt => implicit request =>
    FutureCache.getOrElse("index:metrics", 60 * 5) {
      find[AnyModel](
        defaultParams = SearchParams(
          // we don't need results here because we're only using the facets
          count = Some(0),
          entities = List(
            EntityType.Repository,
            EntityType.DocumentaryUnit,
            EntityType.HistoricalAgent,
            EntityType.Country)
        ),
        facetBuilder = entityMetrics
      ).map(_.page.facets)
    }.map(facets => Ok(p.portal(Stats(facets))))
  }

  def browseItem(entityType: EntityType.Value, id: String) = Action { implicit request =>
    entityType match {
      case EntityType.DocumentaryUnit => Redirect(portalRoutes.browseDocument(id))
      case EntityType.Repository => Redirect(portalRoutes.browseRepository(id))
      case EntityType.HistoricalAgent => Redirect(portalRoutes.browseHistoricalAgent(id))
      case EntityType.Concept => Redirect(portalRoutes.browseConcept(id))
      case EntityType.Country => Redirect(portalRoutes.browseCountry(id))
      case EntityType.Link => Redirect(portalRoutes.browseLink(id))
      case EntityType.Annotation => Redirect(portalRoutes.browseAnnotation(id))
      case EntityType.Vocabulary => Redirect(portalRoutes.browseVocabulary(id))
      case _ => NotFound(renderError("errors.pageNotFound", pageNotFound()))
    }
  }

  def browseCountries = userBrowseAction.async { implicit userDetails => implicit request =>
    find[Country](entities = List(EntityType.Country),
      facetBuilder = countryFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.country.list(page, params, facets, portalRoutes.browseCountries(), userDetails.watchedItems))
    }
  }

  def browseRepositoriesByCountry = userBrowseAction.async { implicit userDetails => implicit request =>
    find[Repository](
      defaultParams = SearchParams(
        sort = Some(SearchOrder.Country),
        entities = List(EntityType.Repository)),
      facetBuilder = repositorySearchFacets
    ).map { case QueryResult(page, params, facets) =>
        Ok(p.repository.listByCountry(page, params, facets,
          portalRoutes.browseRepositoriesByCountry(),
          userDetails.watchedItems))
    }
  }

  def browseCountry(id: String) = getAction[Country](EntityType.Country, id) {
      item => details => implicit userOpt => implicit request =>
    if (isAjax) Ok(p.country.itemDetails(item, details.annotations, details.links, details.watched))
    else Ok(p.country.show(item, details.annotations, details.links, details.watched))
  }

  def searchCountry(id: String) = getAction.async[Country](EntityType.Country, id) {
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

  // The facets for documents within a repository or another document shouldn't
  // contain the holder or country (since they'll be implied)
  private def localDocFacets: RequestHeader => List[FacetClass[Facet]] = {
    docSearchFacets.andThen(fcl => fcl.filterNot { fc =>
      Seq("holder", "country", "source").contains(fc.param)
    })
  }

  def browseRepositories =  userBrowseAction.async { implicit userDetails => implicit request =>
    find[Repository](
      entities = List(EntityType.Repository),
      facetBuilder = repositorySearchFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.repository.list(page, params, facets, portalRoutes.browseRepositories(),
        userDetails.watchedItems))
    }
  }

  def browseRepository(id: String) = getAction[Repository](EntityType.Repository, id) {
      item => details => implicit userOpt => implicit request =>
    if (isAjax) Ok(p.repository.itemDetails(item, details.annotations, details.links, details.watched))
    else Ok(p.repository.show(item, details.annotations, details.links, details.watched))
  }

  def searchRepository(id: String) = getAction.async[Repository](EntityType.Repository, id) {
      item => details => implicit userOpt => implicit request =>
    val filters = (if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)

    find[DocumentaryUnit](
      filters = filters,
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { case QueryResult(page, params, facets) =>
        if(isAjax) Ok(p.repository.childItemSearch(item, page, params, facets,
          portalRoutes.searchRepository(id), details.watched))
        else Ok(p.repository.search(item, page, params, facets,
          portalRoutes.searchRepository(id), details.watched))
    }
  }

  def browseDocuments = userBrowseAction.async { implicit userDetails => implicit request =>
    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[DocumentaryUnit](
      filters = filters,
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = docSearchFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.documentaryUnit.list(page, params, facets, portalRoutes.browseDocuments(),
        userDetails.watchedItems))
    }
  }

  def browseDocument(id: String) = getAction[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      item => details => implicit userOpt => implicit request =>
    if (isAjax) Ok(p.documentaryUnit.itemDetails(item, details.annotations, details.links, details.watched))
    else Ok(p.documentaryUnit.show(item, details.annotations, details.links, details.watched))
  }

  def searchDocument(id: String) = getAction.async[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      item => details => implicit userOpt => implicit request =>
    find[DocumentaryUnit](
      filters = Map(SolrConstants.PARENT_ID -> item.id),
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = localDocFacets,
      defaultOrder = SearchOrder.Id
    ).map { case QueryResult(page, params, facets) =>
      if (isAjax) Ok(p.documentaryUnit.childItemSearch(item, page, params, facets,
          portalRoutes.searchDocument(id), details.watched))
      else Ok(p.documentaryUnit.search(item, page, params, facets,
        portalRoutes.searchDocument(id), details.watched))
    }
  }

  def browseHistoricalAgents = userBrowseAction.async { implicit userDetails => implicit request =>
    find[HistoricalAgent](
      entities = List(EntityType.HistoricalAgent),
      facetBuilder = historicalAgentFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.historicalAgent.list(page, params, facets,
        portalRoutes.browseHistoricalAgents(), userDetails.watchedItems))
    }
  }

  def browseHistoricalAgent(id: String) = getAction[HistoricalAgent](EntityType.HistoricalAgent, id) {
      doc => details => implicit userOpt => implicit request =>
    Ok(p.historicalAgent.show(doc, details.annotations, details.links, details.watched))
  }

  def browseLink(id: String) = getAction[Link](EntityType.Link, id) {
      link => details => implicit userOpt => implicit request =>
    Ok(p.link.show(link))
  }

  def browseAnnotation(id: String) = getAction[Annotation](EntityType.Annotation, id) {
      ann => details => implicit userOpt => implicit request =>
    Ok(p.annotation.show(ann))
  }

  def browseVocabulary(id: String) = getAction[Vocabulary](EntityType.Vocabulary, id) {
    item => details => implicit userOpt => implicit request =>
      if (isAjax) Ok(p.vocabulary.itemDetails(item, details.annotations, details.links, details.watched))
      else Ok(p.vocabulary.show(item, details.annotations, details.links, details.watched))
  }

  def searchVocabulary(id: String) = getAction.async[Vocabulary](EntityType.Vocabulary, id) {
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


  def browseConcept(id: String) = getAction.async[Concept](EntityType.Concept, id) {
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

  def browseConcepts = userBrowseAction.async { implicit userDetails => implicit request =>
    find[Concept](
      entities = List(EntityType.Concept),
      facetBuilder = conceptFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.concept.list(page, params, facets, portalRoutes.browseConcepts(),
        userDetails.watchedItems))
    }
  }

  def itemHistory(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    backend.history[SystemEvent](id, PageParams.fromRequest(request)).map { data =>
      if (isAjax) {
        Ok(p.activity.activityModal(data))
      } else {
        Ok(p.activity.activityModal(data))
      }
    }
  }

  def placeholder = Cached.status(_ => "pages:portalPlaceholder", OK, 60 * 60) {
    Action { implicit request =>
      Ok(views.html.placeholder())
    }
  }

  def dataPolicy = Cached("pages:portalDataPolicy") {
    userProfileAction { implicit userOpt => implicit request =>
      Ok(p.dataPolicy())
    }
  }

  case class NewsItem(title: String, link: String, description: Html, pubDate: Option[DateTime] = None)

  object NewsItem {
    import scala.util.control.Exception._
    import org.joda.time.format.DateTimeFormat
    def fromRss(feed: String): Seq[NewsItem] = {
      val pat = DateTimeFormat.forPattern("EEE, dd MMM yyyy H:m:s Z")
      (xml.XML.loadString(feed) \\ "item").map { item =>
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

  def linkedData(id: String) = userBrowseAction.async { implicit userDetails => implicit request =>

    for {
      ids <- searchLinksForm.bindFromRequest(request.queryString).fold(
        errs => searchLinks(id), {
            case Some(t) => { searchLinks(id, t)}
            case _ => { searchLinks(id) }
        })
      docs <- SearchDAO.listByGid[AnyModel](ids)
    } yield Ok(Json.toJson(docs.zip(ids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }

  def linkedDataInContext(id: String, context: String) = userBrowseAction.async { implicit userDetails => implicit request =>
    for {
      ids <-  searchLinksForm.bindFromRequest(request.queryString).fold(
        errs => searchLinks(id, context=Some(context)), {
            case Some(t) => { searchLinks(id, t, Some(context))}
            case _ => { searchLinks(id, context=Some(context)) }
        })
      docs <- SearchDAO.listByGid[AnyModel](ids)
    } yield Ok(Json.toJson(docs.zip(ids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }
}

