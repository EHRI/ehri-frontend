package controllers.portal

import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.p
import utils.search._
import play.api.libs.json.Json
import play.api.cache.Cached
import defines.EntityType
import play.api.libs.ws.WS
import play.api.templates.Html
import solr.SolrConstants
import backend.Backend
import controllers.base.{SessionPreferences, ControllerHelpers}
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.Logger
import utils._

import com.google.inject._
import play.api.mvc.Results._
import scala.Some
import scala.Some
import views.html.errors.pageNotFound
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

@Singleton
case class Portal @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO)
  extends Controller
  with LoginLogout
  with ControllerHelpers
  with Search
  with FacetConfig
  with PortalActions
  with PortalSocial
  with PortalAnnotations
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
  private val defaultSearchTypes = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent,
    EntityType.Country)
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

  def index = Cached.status(_ => "page:index", OK, 60) {
    userProfileAction.async { implicit userOpt => implicit request =>
      searchAction[AnyModel](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Country),
        entities = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent, EntityType.Country))),
        entityFacets = entityMetrics) { page => params => facets => implicit userOpt => _ =>
        Ok(p.portal(Stats(page.facets)))
      }.apply(request)
    }
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
      case _ => NotFound(renderError("errors.pageNotFound", pageNotFound()))
    }
  }

  def browseCountries = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Country](defaultParams = Some(SearchParams(entities = List(EntityType.Country))),
      entityFacets = countryFacets) {
        page => params => facets => _ => _ =>
      Ok(p.country.list(page, params, facets, portalRoutes.browseCountries(), userDetails.watchedItems))
    }.apply(request)
  }

  def browseRepositoriesByCountry = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Repository](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Country), entities = List(EntityType.Repository))),
      entityFacets = repositorySearchFacets) {
      page => params => facets => _ => _ =>
        Ok(p.repository.listByCountry(page, params, facets, portalRoutes.browseRepositoriesByCountry(),
          userDetails.watchedItems))
    }.apply(request)
  }

  def browseCountry(id: String) = getAction[Country](EntityType.Country, id) {
      item => details => implicit userOpt => implicit request =>
    if (isAjax) Ok(p.country.itemDetails(item, details.annotations, details.links, details.watched))
    else Ok(p.country.show(item, details.annotations, details.links, details.watched))
  }

  def searchCountry(id: String) = getAction.async[Country](EntityType.Country, id) {
      item => details => implicit userOpt => implicit request =>
    searchAction[Repository](Map("countryCode" -> item.id), entityFacets = repositorySearchFacets,
        defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      if (isAjax) Ok(p.country.childItemSearch(item, page, params, facets,
          portalRoutes.searchCountry(id), details.watched))
      else Ok(p.country.search(item, page, params, facets,
          portalRoutes.searchCountry(id), details.watched))
    }.apply(request)
  }

  def browseRepositories =  userProfileAction.async { implicit userOpt => implicit request =>
    watchedItems.flatMap { watched =>
      searchAction[Repository](defaultParams = Some(SearchParams(entities = List(EntityType.Repository))),
        entityFacets = repositorySearchFacets) {
          page => params => facets => implicit userOpt => implicit request =>
        Ok(p.repository.list(page, params, facets, portalRoutes.browseRepositories()))
      }.apply(request)
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
    searchAction[DocumentaryUnit](filters,
      defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchFacets) {
      page => params => facets => _ => _ =>
        if(isAjax) Ok(p.repository.childItemSearch(item, page, params, facets,
          portalRoutes.searchRepository(id), details.watched))
        else Ok(p.repository.search(item, page, params, facets,
          portalRoutes.searchRepository(id), details.watched))
    }.apply(request)
  }

  def browseDocuments = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[DocumentaryUnit](defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchFacets) { page => params => facets => _ => _ =>
      Ok(p.documentaryUnit.list(page, params, facets, portalRoutes.browseDocuments(),
        userDetails.watchedItems))
    }.apply(request)
  }

  def browseDocumentsByRepository = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[DocumentaryUnit](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Holder), entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchRepositoryFacets) {
      page => params => facets => _ => _ =>
        Ok(p.documentaryUnit.listByRepository(page, params, facets, portalRoutes.browseDocumentsByRepository(),
          userDetails.watchedItems))
    }.apply(request)
  }

  def browseDocument(id: String) = getAction[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      item => details => implicit userOpt => implicit request =>
    if (isAjax) Ok(p.documentaryUnit.itemDetails(item, details.annotations, details.links))
    else Ok(p.documentaryUnit.show(item, details.annotations, details.links, details.watched))
  }

  def searchDocument(id: String) = getAction.async[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      item => details => implicit userOpt => implicit request =>
    val filters = Map(SolrConstants.PARENT_ID -> item.id)
    searchAction[DocumentaryUnit](filters,
      defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchFacets) {
      page => params => facets => _ => _ =>
        if (isAjax) Ok(p.documentaryUnit.childItemSearch(item, page, params, facets,
            portalRoutes.searchDocument(id), details.watched))
        else Ok(p.documentaryUnit.search(item, page, params, facets,
          portalRoutes.searchDocument(id), details.watched))
    }.apply(request)
  }

  def browseHistoricalAgents = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[HistoricalAgent](defaultParams = Some(SearchParams(entities = List(EntityType.HistoricalAgent))),
      entityFacets = historicalAgentFacets) {
        page => params => facets => _ => _ =>
      Ok(p.historicalAgent.list(page, params, facets, portalRoutes.browseHistoricalAgents()))
    }.apply(request)
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

  def browseConcept(id: String) = getAction.async[Concept](EntityType.Concept, id) {
    item => details => implicit userOpt => implicit request =>
      searchAction[Concept](Map("parentId" -> item.id),
        entityFacets = conceptFacets,
        defaultParams = Some(SearchParams(entities = List(EntityType.Concept)))) {
        page => params => facets => _ => _ =>
          Ok(p.concept.show(item, page, params, facets,
            portalRoutes.browseConcept(id), details.annotations, details.links, details.watched))
      }.apply(request)
  }

  def browseConcepts = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.concept.list(page, params, facets, portalRoutes.browseConcepts(),
        userDetails.watchedItems))
    }.apply(request)
  }

  def itemHistory(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    backend.history(id, PageParams.fromRequest(request)).map { data =>
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

  def newsFeed = Cached.status(_ => "pages.newsFeed", OK, 60 * 60) {
    Action.async { request =>
      WS.url("http://www.ehri-project.eu/rss.xml").get().map { r =>
        Ok(p.newsFeed(NewsItem.fromRss(r.body)))
      }
    }
  }
}

