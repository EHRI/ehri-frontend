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
import play.api.libs.json.Json
import play.api.libs.json.JsValue

import com.google.inject._
import play.api.Play.current
import models.GuidePage.Layout
import models.GeoCoordinates

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
  def getParams(request: Request[Any], eT: EntityType.Value): Option[SearchParams] = {
    request.getQueryString("parent") match {
      case Some(parent) => Some(SearchParams(query = Some("parentId:" + parent), entities = List(eT)))
      case _ => {
        Some(SearchParams(query = Some("isTopLevel:true"), entities = List(eT)))
      }
    }
  }

  /*
  * Return Map extras param if needed
  */
  def mapParams(request: Request[AnyContent])(f: Map[String, String] => Option[String]) = {
    GeoCoordinates.form.bindFromRequest(request.queryString).fold(
      errorForm => {
        f(Map.empty)(SearchOrder.Name)
      },
      latlng => { 
        f(Map("pt" -> latlng, "sfield" -> "location", "sort" -> "geodist() asc"))(SearchOrder.Location)
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
        /*
        Json.toJson(item)(Concept.Converter.clientFormat)
        */
      }.toList.flatten
  }

  /*
  * MAP Functions
  *   -> ?wt=json&indent=true&fl=name,location,id&q=*:*&sfield=location&pt=47.17701840683218,17.38037109375&sort=geodist%28%29%20asc&fq=type%3A%28cvocConcept%29&fq=accessibleTo%3AALLUSERS&fq=holderId%3A%22terezin-places%22&rows=20
  */

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
    searchAction[HistoricalAgent](params, defaultParams = Some(SearchParams(entities = List(EntityType.HistoricalAgent)))
    ) {
      page => params => facets => _ => _ =>
        if (isAjax) Ok(p.guides.ajax(template -> guide, page, params))
        else Ok(p.guides.person(template -> (guide -> guide.findPages), page, params))
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
        else Ok(p.guides.keywords(template -> (guide -> guide.findPages), page, params))
    }.apply(request)
  }

  /*
  *   Layout named "map" [Concept]
  */
  def guideMap(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
    mapParams(request) { extra => sort =>
      searchAction[Concept](
        params, 
        extra =  extra,
        defaultParams = Some(SearchParams(
          entities = List(EntityType.Concept), 
          sort = sort
        )),
        entityFacets = conceptFacets) {
        page => params => facets => _ => _ =>
        render {
          case Accepts.Html() => {
            if (isAjax) Ok(p.guides.ajax(template -> guide, page, params))
            else Ok(p.guides.places(template -> (guide -> guide.findPages), page, params))
          }
          case Accepts.Json() => {
            Ok(Json.toJson(returnJson(page)))
          }
        }
      }.apply(request)
    }
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
        else Ok(p.guides.organisation(template -> (guide -> guide.findPages), page, params))
    }.apply(request)
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