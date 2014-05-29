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
import models.GuidePage

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

  private def pageNotFound() = Action { implicit request =>
    NotFound(views.html.errors.pageNotFound())
  }

  def itemOr404Action(f: => Option[Action[AnyContent]]): Action[AnyContent] = {
    f.getOrElse(pageNotFound())
  }

  /*
  * Return a list of guides
  */
  def listGuides() = userProfileAction { implicit userOpt => implicit request =>
    Ok(p.guides.guidesList(Guide.findAll(active = true)))
  }

  /*
  * Return a homepage for a guide
  */
  def home(path: String) = itemOr404Action {
    Guide.find(path, active = true).map { guide =>
      guideLayout(guide, guide.getDefaultPage)
    }
  }

  /*
  * Return a layout for a guide and a given path
  */
  def layoutRetrieval(path: String, page: String) = itemOr404Action {
    Guide.find(path, active = true).map  { guide =>
      guideLayout(guide, guide.getPage(page))
    }
  }


  /*
  *
  * Link a layout [GuidePage] to a correct template function
  *
  */


  def guideLayout(guide: Guide, temp: Option[GuidePage]) = itemOr404Action {
    temp.map {
      case page if page.layout == "person" => guideAuthority(page, Map("holderId" -> page.content), guide)
      case page if page.layout == "map" => guideMap(page, Map("holderId" -> page.content), guide)
      case page if page.layout == "keyword" => guideKeyword(page, Map("holderId" -> page.content), guide)
      case page if page.layout == "organisation" => guideOrganization(page, Map("holderId" -> page.content), guide)
      case page if page.layout == "md" => guideMarkdown(page, page.content, guide)
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
  def guideAuthority(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
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
  def guideKeyword(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
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
  def guideMap(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
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
  def guideOrganization(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
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
  def guideMarkdown(template: GuidePage, content: String, guide: Guide) = userProfileAction { implicit userOpt => implicit request =>
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
  def guideFacets(path: String) = itemOr404Action {
    Guide.find(path, active = true).map { guide =>
      userProfileAction { implicit userOpt => implicit request =>
        Ok(p.guides.facet(GuidePage.faceted -> (guide -> guide.getPages)))
      }
    }
  }
}