package controllers.portal

import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.mvc._
import views.html.p
import utils.search._
import defines.EntityType
import backend.Backend
import controllers.base.{SessionPreferences, ControllerHelpers}
import utils._
import models.Guide
import models.GuidesPage

import com.google.inject._

case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountDAO)
  extends ControllerHelpers
  with Search
  with FacetConfig
  with PortalActions
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val portalRoutes = controllers.portal.routes.Portal

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private val defaultSearchTypes = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent,
    EntityType.Country)
  private val defaultSearchParams = SearchParams(entities = defaultSearchTypes, sort = Some(SearchOrder.Score))

  /*
  *
  *
  *   General functionnalities for SQL DATA retrieval
  *
  */

  /*
  *  Return SearchParams for items with hierarchy
  */
  def getParams(request: Request[Any], eT: EntityType.Value, hierarchy: Boolean = false): Option[SearchParams] = {
    request.getQueryString("parent") match {
      case Some(parent) => Some(SearchParams(query = Some("parentId:" + parent), entities = List(eT)))
      case _ => {
        hierarchy match {
          case true => Some(SearchParams(query = Some("isTopLevel:true"), entities = List(eT)))
          case _ => Some(SearchParams(entities = List(eT)))
        }

      }
    }
  }

  /*
  *
  *   Routes functions for normal HTML
  *
  */

  private def itemNotFound(item: String) = Action {
    implicit request =>
      NotFound(views.html.errors.itemNotFound(Some(item)))
  }

  private def pageNotFound() = Action {
    implicit request =>
      NotFound(views.html.errors.pageNotFound())
  }

  /*
  * Return a list of guides
  */
  def listGuides() = userProfileAction {
    implicit userOpt => implicit request =>
      Ok(p.guides.guidesList(Guide.findAll(active = true)))
  }

  /*
  * Return a homepage for a guide
  */
  def home(path: String) = {
    Guide.find(path, active = true) match {
      /* We need active guide only */
      case Some(guide) => guideLayout(guide, guide.getDefaultPage)
      case _ => itemNotFound(path)
    }
  }

  /*
  * Return a layout for a guide and a given path
  */
  def layoutRetrieval(path: String, page: String) = {
    Guide.find(path, active = true) match {
      /* We need active guide only */
      case Some(guide) => guideLayout(guide, guide.getPage(page))
      case _ => itemNotFound(path)
    }
  }


  /*
  *
  * Link a layout [GuidesPage] to a correct template function
  *
  */


  def guideLayout(guide: Guide, temp: Option[GuidesPage]) = {
    /* Function mapping */
    temp match {
      case Some(t) if t.layout == "person" => guideAuthority(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "map" => guideMap(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "keyword" => guideKeyword(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "organisation" => guideOrganization(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "md" => guideMarkdown(t, t.content, guide)
      case _ => pageNotFound()
    }
  }


  /*
  *
  *   Layouts function, add a new function for a new type of layout
  *
  */

  /*
  *   Layout named "person" [HistoricalAgent]
  */
  def guideAuthority(template: GuidesPage, params: Map[String, String], guide: Guide) = userBrowseAction.async {
    implicit userDetails => implicit request =>
      searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities = List(EntityType.HistoricalAgent)))
      ) {
        page => params => facets => _ => _ =>
          if (isAjax) Ok(p.guides.ajax(template -> guide, page, params))
          else Ok(p.guides.person(template -> (guide -> guide.getPages), page, params))
      }.apply(request)
  }

  /*
  *   Layout named "keyword" [Concept]
  */
  def guideKeyword(template: GuidesPage, params: Map[String, String], guide: Guide) = userBrowseAction.async {
    implicit userDetails => implicit request =>
      searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
        entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
          if (isAjax) Ok(p.guides.ajax(template -> guide, page, params))
          else Ok(p.guides.keywords(template -> (guide -> guide.getPages), page, params))
      }.apply(request)
  }

  /*
  *   Layout named "map" [Concept]
  */
  def guideMap(template: GuidesPage, params: Map[String, String], guide: Guide) = userBrowseAction.async {
    implicit userDetails => implicit request =>
      searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
        entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
          if (isAjax) Ok(p.guides.ajax(template -> guide, page, params))
          else Ok(p.guides.places(template -> (guide -> guide.getPages), page, params))
      }.apply(request)
  }

  /*
  *   Layout named "organisation" [Concept]
  */
  def guideOrganization(template: GuidesPage, params: Map[String, String], guide: Guide) = userBrowseAction.async {
    implicit userDetails => implicit request =>
      searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept),
        entityFacets = conceptFacets
      ) {
        page => params => facets => _ => _ =>
          if (isAjax) Ok(p.guides.ajax(template -> guide, page, params))
          else Ok(p.guides.keywords(template -> (guide -> guide.getPages), page, params))
      }.apply(request)
  }

  /*
  *   Layout named "md" [Markdown]
  */
  def guideMarkdown(template: GuidesPage, content: String, guide: Guide) = userProfileAction {
    implicit userOpt => implicit request =>
      Ok(p.guides.markdown(template -> (guide -> guide.getPages), content))
  }


  /*
  *
  * Ajax functionnalities for guides
  *
  * Can be tested through
      $.post(
      "http://localhost:9000/guides/:guide/:page",
      {},
      function(data) {},
      "html")
  *
  *
  */

  /*
  *   Faceted search
  */
  def guideFacets(path: String) = {
    Guide.find(path, active = true) match {
      /* We need active guide only */
      case Some(guide) => userProfileAction {
        implicit userOpt => implicit request =>
          Ok(p.guides.facet(GuidesPage.faceted -> (guide -> guide.getPages)))
      }
      case _ => itemNotFound(path)
    }
  }
}