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
<<<<<<< HEAD
import models.GuidesData
=======
import models.Guide
>>>>>>> guides
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

<<<<<<< HEAD
  def facetedPage(): GuidesPage = {
    GuidesPage(
      None,
      "facet",
      "portal.guides.faceted",
      "browse",
      "top",
      "",
      0
    )
  }
  /*
  *  Return a menu
  */
  def menu(id: Int):List[GuidesPage] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT 
          *
        FROM research_guide_page 
        WHERE id_research_guide = {id}
      """
    ).on('id -> id).apply().map { row =>
      GuidesPage(
        row[Option[Int]]("id_research_guide_page"),
        row[String]("layout_research_guide_page"),
        row[String]("name_research_guide_page"),
        row[String]("path_research_guide_page"),
        row[String]("menu_research_guide_page"),
        row[String]("cypher_research_guide_page"),
        row[Int]("id_research_guide")
      )
    }.toList
  }

  /*
  *  Return a template
  */
  def template(guide:String, key:String): Option[GuidesPage] = DB.withConnection { implicit connection =>
      SQL(
        """
          SELECT 
            rgp.id_research_guide_page,
            rgp.layout_research_guide_page,
            rgp.name_research_guide_page,
            rgp.path_research_guide_page,
            rgp.menu_research_guide_page,
            rgp.cypher_research_guide_page,
            rg.id_research_guide
          FROM 
            research_guide_page rgp,
            research_guide rg
          WHERE 
            rgp.path_research_guide_page = {id} AND 
            rgp.id_research_guide = rg.id_research_guide AND
            rg.path_research_guide = {guide}
          LIMIT 1
        """
      ).on('id -> key, 'guide -> guide).apply().headOption.map { row =>
        GuidesPage(
          row[Option[Int]]("id_research_guide_page"),
          row[String]("layout_research_guide_page"),
          row[String]("name_research_guide_page"),
          row[String]("path_research_guide_page"),
          row[String]("menu_research_guide_page"),
          row[String]("cypher_research_guide_page"),
          row[Int]("id_research_guide")
        )
      }
    }

  /*
  *  Return a guide given its ID
  */
  def guide(guideId: Int=0, guidePath: String = ""): GuidesData = DB.withConnection { implicit connection =>
    if(guideId > 0) {
      SQL(
        """
          SELECT 
            * 
          FROM 
            research_guide
          WHERE 
            id_research_guide = {id} 
          LIMIT 1
        """
      ).on('id -> guideId).apply().headOption.map { row =>
        GuidesData(
          row[Option[Int]]("id_research_guide"),
          row[String]("name_research_guide"),
          row[String]("path_research_guide"),
          row[Option[String]]("picture_research_guide"),
          row[Option[String]]("description_research_guide")
        )
      }.head
    } else {
      SQL(
        """
          SELECT 
            * 
          FROM 
            research_guide
          WHERE 
            path_research_guide = {path} 
          LIMIT 1
        """
      ).on('path -> guidePath).apply().headOption.map { row =>
        GuidesData(
          row[Option[Int]]("id_research_guide"),
          row[String]("name_research_guide"),
          row[String]("path_research_guide"),
          row[Option[String]]("picture_research_guide"),
          row[Option[String]]("description_research_guide")
        )
      }.head
    }
  }


=======
>>>>>>> guides
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
<<<<<<< HEAD

    val guides: List[GuidesData] = DB.withConnection { implicit connection =>
      SQL(
        """
          SELECT * FROM research_guide
        """
      ).apply().map { row =>
        GuidesData(
          row[Option[Int]]("id_research_guide"),
          row[String]("name_research_guide"),
          row[String]("path_research_guide"),
          row[Option[String]]("picture_research_guide"),
          row[Option[String]]("description_research_guide")
        )
      }.toList
    }
    Ok(p.guides.guidesList(guides))
=======
    Ok(p.guides.guidesList(Guide.findAll(true)))
>>>>>>> guides
  }

  /*
  * Return a homepage for a guide
  */
<<<<<<< HEAD
  def home(guide: String) = {
    guideLayout(template(guide, "places"))
=======
  def home(path: String) = {
    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => guideLayout(GuidesPage.find(guide.objectId, guide.default), guide)
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
>>>>>>> guides
  }

  /*
  * Return a layout for a guide and a given path
  */
<<<<<<< HEAD
  def layoutRetrieval(guide:String, key: String) = { 
    guideLayout(template(guide, key))
=======
  def layoutRetrieval(path:String, page: String) = { 
    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => guideLayout(GuidesPage.find(guide.objectId, page), guide)
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
>>>>>>> guides
  }



/*
*
* Link a layout [GuidesPage] to a correct template function
*
*/




<<<<<<< HEAD
  def guideLayout(temp: Option[GuidesPage]) = {
    /* Function mapping */
    temp match {
      case Some(t) if t.layout == "person" => guideAuthority(t, Map("holderId" -> t.content))
      case Some(t) if t.layout == "map" => guideMap(t, Map("holderId" -> t.content))
      case Some(t) if t.layout == "keyword" => guideKeyword(t, Map("holderId" -> t.content))
      case Some(t) if t.layout == "organisation" => guideOrganization(t, Map("holderId" -> t.content))
      case Some(t) if t.layout == "md" => guideMarkdown(t, t.content)
=======
  def guideLayout(temp: Option[GuidesPage], guide: Guide) = {
    /* Function mapping */
    temp match {
      case Some(t) if t.layout == "person" => guideAuthority(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "map" => guideMap(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "keyword" => guideKeyword(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "organisation" => guideOrganization(t, Map("holderId" -> t.content), guide)
      case Some(t) if t.layout == "md" => guideMarkdown(t, t.content, guide)
>>>>>>> guides
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
<<<<<<< HEAD
  def guideAuthority(template: GuidesPage, params: Map[String, String]) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))
      ) {
        page => params => facets => _ => _ =>
      Ok(p.guides.person(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
  def guideAuthority(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))
      ) {
        page => params => facets => _ => _ =>
      Ok(p.guides.person(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
>>>>>>> guides

    }.apply(request)
  }

  /*
  *   Layout named "keyword" [Concept]
  */
<<<<<<< HEAD
  def guideKeyword(template: GuidesPage, params: Map[String, String]) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.guides.keywords(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
  def guideKeyword(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.guides.keywords(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
>>>>>>> guides
    }.apply(request)
  }

  /*
  *   Layout named "map" [Concept]
  */
<<<<<<< HEAD
  def guideMap(template: GuidesPage, params: Map[String, String]) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.guides.places(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
  def guideMap(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    searchAction[Concept](params, defaultParams = Some(SearchParams(entities = List(EntityType.Concept))),
      entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
      Ok(p.guides.places(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
>>>>>>> guides
    }.apply(request)
  }

  /*
  *   Layout named "organisation" [Concept]
  */
<<<<<<< HEAD
  def guideOrganization(template: GuidesPage, params: Map[String, String]) = userBrowseAction.async { implicit userDetails => implicit request =>
=======
  def guideOrganization(template: GuidesPage, params: Map[String, String], guide:Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
>>>>>>> guides
    searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept),
      entityFacets = conceptFacets
    ) {
        page => params => facets => _ => _ =>
<<<<<<< HEAD
      Ok(p.guides.keywords(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
      Ok(p.guides.keywords(template -> (guide -> GuidesPage.findAll(guide.objectId)), page, params))
>>>>>>> guides
    }.apply(request)
  }

  /*
  *   Layout named "md" [Markdown]
  */
<<<<<<< HEAD
  def guideMarkdown(template: GuidesPage, content: String) = userProfileAction { implicit userOpt => implicit request =>
    Ok(p.guides.markdown(template -> (guide(template.parent) -> menu(template.parent)), content))
=======
  def guideMarkdown(template: GuidesPage, content: String, guide:Guide) = userProfileAction { implicit userOpt => implicit request =>
    Ok(p.guides.markdown(template -> (guide -> GuidesPage.findAll(guide.objectId)), content))
>>>>>>> guides
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
<<<<<<< HEAD
  def layoutAjaxRetrieval(guide:String, key:String) = {
    /* Function mapping */
    template(guide, key) match {
      case Some(t) => guideAjax(t, Map("holderId" -> t.content))
=======
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
>>>>>>> guides
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
<<<<<<< HEAD
  def guideAjax(template: GuidesPage, params: Map[String, String]) = {
=======
  def guideAjax(template: GuidesPage, params: Map[String, String], guide: Guide) = {
>>>>>>> guides
    template match {
      case t if template.layout == "organisation" => userBrowseAction.async { implicit userOpt => implicit request =>
        searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept, true),
          entityFacets = conceptFacets
        ) {
            page => params => facets => _ => _ =>
<<<<<<< HEAD
              Ok(p.guides.ajax(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
              Ok(p.guides.ajax(template -> guide, page, params))
>>>>>>> guides
        }.apply(request)
      }
      case t if template.layout == "keyword" | template.layout == "map" => userBrowseAction.async { implicit userOpt => implicit request =>
        searchAction[Concept](params, defaultParams = getParams(request, EntityType.Concept),
          entityFacets = conceptFacets
        ) {
            page => params => facets => _ => _ =>
<<<<<<< HEAD
              Ok(p.guides.ajax(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
              Ok(p.guides.ajax(template -> guide , page, params))
>>>>>>> guides
        }.apply(request)
      }
      case t if template.layout == "person" => userBrowseAction.async { implicit userDetails => implicit request =>
        searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))
          ) {
            page => params => facets => _ => _ =>
<<<<<<< HEAD
          Ok(p.guides.ajax(template -> (guide(template.parent) -> menu(template.parent)), page, params))
=======
          Ok(p.guides.ajax(template -> guide , page, params))
>>>>>>> guides

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
<<<<<<< HEAD
  def guideFacets(guidePath: String) = {
    val gui = guide(guidePath = guidePath)
    gui.objectId match {
      case Some(id) => userProfileAction { implicit userOpt => implicit request =>
        Ok(p.guides.facet(facetedPage() -> (gui -> menu(id))))
=======
  def guideFacets(path: String) = { 
    Guide.find(path, 1) match { /* We need active guide only */
      case Some(guide) => userProfileAction { implicit userOpt => implicit request =>
        Ok(p.guides.facet(GuidesPage.faceted -> (guide -> GuidesPage.findAll(guide.objectId))))
>>>>>>> guides
      }
      case _ => Action { implicit request =>
        NotFound(views.html.errors.pageNotFound())
      }
    }
<<<<<<< HEAD
    
=======
>>>>>>> guides
  }

}