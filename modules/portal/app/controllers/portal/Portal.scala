package controllers.portal

import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.p

import com.google.inject._
import utils.search._
import play.api.libs.json.{Writes, Json}
import play.api.cache.Cached
import defines.{ContentTypes, EntityType}
import utils.{SystemEventParams, ListParams}
import play.api.libs.ws.WS
import play.api.templates.Html
import solr.SolrConstants
import backend.{ApiUser, Backend}
import controllers.base.ControllerHelpers


@Singleton
case class Portal @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend)
  extends Controller with ControllerHelpers with Search with FacetConfig with PortalActions with PortalProfile with PortalSocial {

  // This is a publically-accessible site
  override val staffOnly = false

  private val portalRoutes = controllers.portal.routes.Portal


  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private val defaultSearchTypes = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent)
  private val defaultSearchParams = SearchParams(entities = defaultSearchTypes, sort = Some(SearchOrder.Score))


  def search = searchAction[AnyModel](defaultParams = Some(defaultSearchParams),
        entityFacets = globalSearchFacets, mode = SearchMode.DefaultNone) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(p.search(page, params, facets, portalRoutes.search))
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
      "items" -> page.items.map { case (id, name, t) =>
        Json.arr(id, name, t.toString)
      }
    ))
  }

  def account = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt.flatMap(_.account)))
  }

  def index = Cached("pages.landing", 3600) {
    userProfileAction.async { implicit userOpt => implicit request =>
      searchAction[Repository](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Country),
        entities = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent))),
        entityFacets = entityMetrics) {
        page => params => facets => implicit userOpt => _ =>
          Ok(p.portal(Stats(page.facets)))
      }.apply(request)
    }
  }

  def browseCountries = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Country](defaultParams = Some(SearchParams(entities = List(EntityType.Country))),
      entityFacets = countryFacets) {
        page => params => facets => _ => _ =>
      Ok(p.country.list(page, params, facets, portalRoutes.browseCountries, userDetails.watchedItems))
    }.apply(request)
  }

  def browseRepositoriesByCountry = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Repository](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Country), entities = List(EntityType.Repository))),
      entityFacets = repositorySearchFacets) {
      page => params => facets => _ => _ =>
        Ok(p.repository.listByCountry(page, params, facets, portalRoutes.browseRepositoriesByCountry,
          userDetails.watchedItems))
    }.apply(request)
  }

  def browseCountry(id: String) = getAction.async[Country](EntityType.Country, id) {
      item => details => implicit userOpt => implicit request =>
    searchAction[Repository](Map("countryCode" -> item.id), entityFacets = repositorySearchFacets,
        defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      Ok(p.country.show(item, page, params, facets,
          portalRoutes.browseCountry(id), details.annotations, details.links, details.watched))
    }.apply(request)
  }

  def browseRepositories =  userProfileAction.async { implicit userOpt => implicit request =>
    watchedItems.flatMap { watched =>
      searchAction[Repository](defaultParams = Some(SearchParams(entities = List(EntityType.Repository))),
        entityFacets = repositorySearchFacets) {
          page => params => facets => implicit userOpt => implicit request =>
        Ok(p.repository.list(page, params, facets, portalRoutes.browseRepositories))
      }.apply(request)
    }
  }

  def browseRepository(id: String) = getAction.async[Repository](EntityType.Repository, id) {
      item => details => implicit userOpt => implicit request =>
    val filters = (if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)
    watchedItems.flatMap { watched =>
      searchAction[DocumentaryUnit](filters,
          defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
          entityFacets = docSearchFacets) {
          page => params => facets => _ => _ =>
        Ok(p.repository.show(item, page, params, facets,
            portalRoutes.browseRepository(id), details.annotations, details.links, details.watched))
      }.apply(request)
    }
  }

  def browseDocuments = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[DocumentaryUnit](defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchFacets) { page => params => facets => _ => _ =>
      Ok(p.documentaryUnit.list(page, params, facets, portalRoutes.browseDocuments,
        userDetails.watchedItems))
    }.apply(request)
  }

  def browseDocumentsByRepository = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[DocumentaryUnit](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Holder), entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchRepositoryFacets) {
      page => params => facets => _ => _ =>
        Ok(p.documentaryUnit.listByRepository(page, params, facets, portalRoutes.browseDocumentsByRepository,
          userDetails.watchedItems))
    }.apply(request)
  }

  def browseDocument(id: String) = getAction.async[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      item => details => implicit userOpt => implicit request =>
    val filters = Map(SolrConstants.PARENT_ID -> item.id)
    searchAction[DocumentaryUnit](filters,
      defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchFacets) {
      page => params => facets => _ => _ =>
        Ok(p.documentaryUnit.show(item, page, params, facets,
          portalRoutes.browseDocument(id), details.annotations, details.links, details.watched))
    }.apply(request)
  }

  def browseHistoricalAgents = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[HistoricalAgent](defaultParams = Some(SearchParams(entities = List(EntityType.HistoricalAgent))),
      entityFacets = historicalAgentFacets) {
        page => params => facets => _ => _ =>
      Ok(p.historicalAgent.list(page, params, facets, portalRoutes.browseHistoricalAgents))
    }.apply(request)
  }


  def browseAuthoritativeSet(id: String) = getAction.async[AuthoritativeSet](EntityType.AuthoritativeSet, id) {
      item => details => implicit userOpt => implicit request =>
    val filters = (if (request.getQueryString(SearchParams.QUERY).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)
    searchAction[HistoricalAgent](filters,
        defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
        entityFacets = globalSearchFacets) {
        page => params => facets => _ => _ =>
      Ok("TODO!")
    }.apply(request)
  }

  def browseHistoricalAgent(id: String) = getAction[HistoricalAgent](EntityType.HistoricalAgent, id) {
      doc => details => implicit userOpt => implicit request =>
    Ok(p.historicalAgent.show(doc, details.annotations, details.links, details.watched))
  }

  def activity = listAction[SystemEvent](EntityType.SystemEvent) {
      list => params => implicit userOpt => implicit request =>
    Ok(p.activity(list, params))
  }

  def activityMore(offset: Int) = listAction[SystemEvent](EntityType.SystemEvent, Some(ListParams(offset = offset))) {
      list => params => implicit userOpt => implicit request =>
    Ok(p.common.eventItems(list))
  }

  import play.api.data.Form
  import play.api.data.Forms._

  private val activityForm = Form(
    tuple(
      "watched" -> default(boolean, true),
      "follows" -> default(boolean, true)
    )
  )

  def personalisedActivity = withUserAction.async { implicit user => implicit request =>
    val listParams = ListParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
    backend.listEventsForUser(user.id, listParams, eventFilter).map { events =>
      Ok(p.activity(events, listParams))
    }
  }

  def personalisedActivityMore(offset: Int) = withUserAction.async { implicit user => implicit request =>
    val listParams = ListParams.fromRequest(request).copy(offset = offset)
    val eventFilter = SystemEventParams.fromRequest(request)
    backend.listEventsForUser(user.id, listParams, eventFilter).map { events =>
      Ok(p.common.eventItems(events))
    }
  }

  // Ajax
  def annotateDoc(id: String) = annotationAction[DocumentaryUnit](id, ContentTypes.DocumentaryUnit) {
      doc => form => implicit userOpt => implicit request =>
    Ok(p.common.annotationForm(doc, form, portalRoutes.annotateDocPost(id),
      portalRoutes.browseDocument(id)))
  }

  // Ajax
  def annotateDocPost(id: String) = annotationPostAction[DocumentaryUnit](id, ContentTypes.DocumentaryUnit) {
      formOrAnn => implicit userOpt => implicit request =>
    formOrAnn match {
      case Right(ann) => Ok(p.common.annotation(ann))
      case Left(err) => {
        println(err)
        Ok(err.errorsAsJson)
      }
    }
  }

  def placeholder = Cached("pages:portalPlaceholder") {
    Action { implicit request =>
      Ok(views.html.placeholder())
    }
  }

  case class NewsItem(title: String, link: String, description: Html)

  object NewsItem {
    def fromRss(feed: String): Seq[NewsItem] = {
      (xml.XML.loadString(feed) \\ "item").map { item =>
        new NewsItem(
          (item \ "title").text,
          (item \ "link").text,
          Html((item \ "description").text))
      }
    }
  }

  def newsFeed = Cached("pages.newsFeed", 3600) {
    Action.async { request =>
      WS.url("http://www.ehri-project.eu/rss.xml").get.map { r =>
        Ok(p.newsFeed(NewsItem.fromRss(r.body)))
      }
    }
  }
}

