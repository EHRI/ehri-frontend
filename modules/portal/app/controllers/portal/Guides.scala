package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic.Search
import models._
import play.api.mvc._
import views.html.p
import utils.search._
import defines.EntityType
import backend.Backend
import controllers.base.{SessionPreferences, ControllerHelpers}
import utils._
import models.Guide
import models.GuidePage
import play.api.libs.json.Json
import play.api.libs.json.JsValue

import com.google.inject._
import play.api.Play.current
import models.GuidePage.Layout
import models.GeoCoordinates
import solr.SolrConstants

case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountDAO)
  extends ControllerHelpers
  with Search
  with FacetConfig
  with PortalActions
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)


  /*
  *
  *
  *   General functionnalities for SQL DATA retrieval
  *
  */

  /*
  *  Return SearchParams for items with hierarchy
  */
  def getParams(request: Request[Any], eT: EntityType.Value): SearchParams = {
    request.getQueryString("parent") match {
      case Some(parent) => SearchParams(query = Some(SolrConstants.PARENT_ID + ":" + parent), entities = List(eT))
      case _ => SearchParams(query = Some(SolrConstants.TOP_LEVEL + ":" + true), entities = List(eT))
    }
  }

  /*
  * Return Map extras param if needed
  */
  def mapParams(request: Map[String,Seq[String]]): (utils.search.SearchOrder.Value, Map[String, Any]) = {
    GeoCoordinates.form.bindFromRequest(request).fold(
      errorForm => {
        (SearchOrder.Name -> Map.empty)
      },
      latlng => { 
        (SearchOrder.Location -> Map("pt" -> latlng.toString, "sfield" -> "location", "sort" -> "geodist() asc"))
      }
    )
  }

  def returnJson(hits:utils.search.ItemPage[(models.Concept, utils.search.SearchHit)]): List[JsValue] = {

      hits.items.map { case(item, id) =>
        item.descriptions.map { case(desc) =>
          Json.toJson(Map(
            "id" -> item.id,
            "latitude" -> desc.latitude.getOrElse(None).toString,
            "longitude" -> desc.longitude.getOrElse(None).toString,
            "name" -> desc.name.toString
          ))
        }.toList
      }.toList.flatten
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
    Ok(p.guides.guidesList(Guide.findAll(activeOnly = true)))
  }

  /*
  * Return a homepage for a guide
  */
  def home(path: String) = itemOr404Action {
    Guide.find(path, activeOnly = true).map { guide =>
      guideLayout(guide, guide.getDefaultPage)
    }
  }

  /*
  * Return a layout for a guide and a given path
  */
  def layoutRetrieval(path: String, page: String) = itemOr404Action {
    Guide.find(path, activeOnly = true).map  { guide =>
      guideLayout(guide, guide.findPage(page))
    }
  }


  /*
  *
  * Link a layout [GuidePage] to a correct template function
  *
  */


  def guideLayout(guide: Guide, temp: Option[GuidePage]) = itemOr404Action {
    println(guide + " -> "+ temp)
    temp.map { page =>
      page.layout match {
        case Layout.Person => guideAuthority(page, Map("holderId" -> page.content), guide)
        case Layout.Map => guideMap(page, Map("holderId" -> page.content), guide)
        case Layout.Keyword => guideKeyword(page, Map("holderId" -> page.content), guide)
        case Layout.Organisation => guideOrganization(page, Map("holderId" -> page.content), guide)
        case Layout.Markdown => guideMarkdown(page, page.content, guide)
        case _ => pageNotFound()
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
  def guideAuthority(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    find[HistoricalAgent](filters = params, entities = List(EntityType.HistoricalAgent)).map { r =>
      if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params))
      else Ok(p.guides.person(template -> (guide -> guide.findPages), r.page, r.params))
    }
  }

  /*
  *   Layout named "keyword" [Concept]
  */
  def guideKeyword(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    find[Concept](filters = params, entities = List(EntityType.Concept), facetBuilder = conceptFacets).map { r =>
      if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params))
      else Ok(p.guides.keywords(template -> (guide -> guide.findPages), r.page, r.params))
    }
  }

  /*
  *   Layout named "map" [Concept]
  */
  def guideMap(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    mapParams(
        if (request.queryString.contains("lat") && request.queryString.contains("lng")) {
          request.queryString
        } else {
          template.getParams()
        }
      ) match { case (sort, geoloc) =>
      find[Concept](
        params, 
        extra = geoloc,
        defaultParams = SearchParams(
          entities = List(EntityType.Concept), 
          sort = Some(sort)
        ),
        entities = List(EntityType.Concept), 
        facetBuilder = conceptFacets).map { r =>
          render {
            case Accepts.Html() => {
              if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params))
              else Ok(p.guides.places(template -> (guide -> guide.findPages), r.page, r.params))
            }
            case Accepts.Json() => {
              Ok(Json.toJson(returnJson(r.page)))
            }
          }
      }
    }
  }

  /*
  *   Layout named "organisation" [Concept]
  */
  def guideOrganization(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    find[Concept](params, defaultParams = getParams(request, EntityType.Concept), facetBuilder = conceptFacets).map { r =>
      if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params))
      else Ok(p.guides.organisation(template -> (guide -> guide.findPages), r.page, r.params))
    }
  }

  /*
  *   Layout named "md" [Markdown]
  */
  def guideMarkdown(template: GuidePage, content: String, guide: Guide) = userProfileAction { implicit userOpt => implicit request =>
    Ok(p.guides.markdown(template -> (guide -> guide.findPages), content))
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
    Guide.find(path, activeOnly = true).map { guide =>
      userProfileAction { implicit userOpt => implicit request =>
        Ok(p.guides.facet(GuidePage.faceted -> (guide -> guide.findPages)))
      }
    }
  }
}