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
import language.postfixOps
import play.api.db._
import play.api.Play.current
import models.Guide
import models.GuidesPage

import anorm._
import anorm.SqlParser._

import com.google.inject._
import play.api.mvc.Results._
import views.html.errors.pageNotFound

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
  def getParams(request: Request[Any], eT: EntityType.Value, hierarchy: Boolean = false ): Option[SearchParams] = {
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

  /*
  * Return a list of guides
  */
def listGuides() = userProfileAction { implicit userOpt => implicit request => 
    Ok(p.guides.guidesList(Guide.findAll(true)))
  }

  /*
  * Return a homepage for a guide
  */
  def home(path: String) = {
    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => guideLayout(GuidesPage.find(guide.objectId, guide.default), guide)
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
  }

  /*
  * Return a layout for a guide and a given path
  */
  def layoutRetrieval(path:String, page: String) = { 
    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => guideLayout(GuidesPage.find(guide.objectId, page), guide)
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
  }



/*
*
* Link a layout [GuidesPage] to a correct template function
*
*/




  def guideLayout(temp: Option[GuidesPage], guide: Guide) = {
    /* Function mapping */
    temp match {
      case Some(t) if t.layout == "person" => guideAuthority(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "map" => guideMap(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "keyword" => guideKeyword(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "organisation" => guideOrganization(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "md" => guideMarkdown(t, t.content, guide)
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
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
  def guideAuthority(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))
      ) {
        page => params => facets => _ => _ =>
      Ok(p.guides.person(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))

    }.apply(request)
  }

  /*
  *   Layout named "keyword" [Concept]
  */
  def guideKeyword(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.guides.keywords(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
    }.apply(request)
  }

  /*
  *   Layout named "map" [Concept]
  */
  def guideMap(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.guides.places(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
    }.apply(request)
  }

  /*
  *   Layout named "organisation" [Concept]
  */
  def guideOrganization(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept),
      entityFacets = conceptFacets
    ) {
        page => params => facets => _ => _ =>
      Ok(p.guides.keywords(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
    }.apply(request)
  }

  /*
  *   Layout named "md" [Markdown]
  */
  def guideMarkdown(template: GuidesPage, content: String, guide:Guide) = userProfileAction { implicit userOpt => implicit request =>
    Ok(p.guides.markdown(template -> (guide -> GuidesPage.findAll(guide.objectId)), content))
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
  *   Route function, retrieve a layout for given identifiers
  */
  def layoutAjaxRetrieval(path:String, key:String) = {
    /* Function mapping */

    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => {
        GuidesPage.find(guide.objectId, key) match {
          case Some(t) => guideAjax(t, Map("holderId" -> t.content), guide)
          case _ => Action { implicit request =>
            NotFound(views.html.errors.pageNotFound())
          }
        }
      }
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
  }

  /*
  *
  *   Research items and print them given a correct layout
  *
  */
  def guideAjax(template: GuidesPage, params: Map[String, String], guide: Guide) = {
    template match {
      case t if template.layout == "organisation" => userBrowseAction.async { implicit userOpt => implicit request =>
        searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept, true),
          entityFacets = conceptFacets
        ) {
            page => params => facets => _ => _ =>
              Ok(p.guides.ajax(template -> guide, page, params))
        }.apply(request)
      }
      case t if template.layout == "keyword" | template.layout == "map" => userBrowseAction.async { implicit userOpt => implicit request =>
        searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept),
          entityFacets = conceptFacets
        ) {
            page => params => facets => _ => _ =>
              Ok(p.guides.ajax(template -> guide , page, params))
        }.apply(request)
      }
      case t if template.layout == "person" => userBrowseAction.async { implicit userDetails => implicit request =>
        searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))
          ) {
            page => params => facets => _ => _ =>
          Ok(p.guides.ajax(template -> guide , page, params))

        }.apply(request)
      }
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }

    }
    
  }
  /*
  *   Faceted search
  */
  def guideFacets(path: String) = { 
    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => userProfileAction { implicit userOpt => implicit request =>
        Ok(p.guides.facet(GuidesPage.faceted -> (guide -> GuidesPage.findAll(guide.objectId))))
      }
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
  }

}