package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic.Search
import models._
import play.api.mvc._
import views.html.p
import utils.search._
import defines.EntityType
import backend.Backend
import backend.rest.SearchDAO
import controllers.base.{SessionPreferences, ControllerHelpers}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import utils._
import models.Guide
import models.GuidePage
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import models.base.AnyModel

import com.google.inject._
import play.api.Play.current
import models.GuidePage.Layout
import models.GeoCoordinates
import solr.SolrConstants
import backend.rest.cypher.CypherDAO
import play.api.libs.json.{JsString, JsValue, JsArray}
import backend.rest.RestBackend

import play.api.data._
import play.api.data.Forms._

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
  * Form for browse 
  */
  val facetsForm = Form(
    tuple(
      "kw" -> list(text),
      "page" -> optional(number)
    )
  )

  def getFacetQuery(ids: List[String]) : String = {
    ids.map("__ID__:" + _ ).reduce((a, b) => a + " OR " + b)
  }

  /*
  *   Faceted request
  */
  def searchFacets(guide: Guide, ids: List[String]): Future[Seq[Long]] = {
    
    val cypher = new CypherDAO
    val query = 
    s"""
        START 
          virtualUnit = node:entities(__ID__= {guide}), 
          accessPoints = node:entities(" """ ++ getFacetQuery(ids) ++ """ ")
        MATCH 
             (link)-[:inContextOf]->virtualUnit,
            (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
         WHERE doc.__ISA__ = "documentaryUnit"
         WITH collect(accessPoints.__ID__) AS accessPointsId, doc
         WHERE ALL (x IN {accesslist} 
                   WHERE x IN accessPointsId)
         RETURN ID(doc)
        """.stripMargin
    cypher.cypher(query, 
    Map(
      /* All IDS */
      "guide" -> JsString(guide.virtualUnit),
      "accesslist" -> Json.toJson(ids)
      /* End IDS */
    )).map { r =>
      (r \ "data").as[Seq[Seq[Long]]].flatten
    }
  }
  /*
   *    Page defintion
   */
   /*
    * Could be better rewritten
   */
  def facetPage(count: Int, page:Option[Int]) : (Int, Int) = {
    val start = (page.getOrElse(1) - 1) * 10

    if(start > count) {
      val begin = count / 10 * 10 
      val end = (count / 10 * 10) + 10
      println((begin, end, count))
      (begin, end)
    } else {
      val begin = if(start < 0) 0 else start
      val end = if(start < 10) 10 else start+10
      println((begin, end, count))
      (begin, end)
    }
  }
  def facetSlice(ids : Seq[Long], page:Option[Int]) : Seq[Long] = {
    val pages = facetPage(ids.size, page)
    ids.slice(pages._1, pages._2)
  }

  def pagify(docsId : Seq[Long], docsItems: List[DocumentaryUnit], accessPoints: List[AnyModel], page: Option[Int] = None) : ItemPage[DocumentaryUnit] = {
    facetPage(docsId.size, page) match { 
      case (start, end) =>
       ItemPage(docsItems.map { doc =>
          doc
        }, start, end - start, docsId.size, List(), None)
      }
  }

  /*
  *   Faceted search
  */
  def guideFacets(path: String) = userProfileAction.async { implicit userOpt => implicit request =>
    Guide.find(path, activeOnly = true).map { guide =>

      /*
       *  If we have keyword, we make a query 
       */
      if (request.queryString.contains("kw[]")) {
        val (selectedFacets, page) = facetsForm.bindFromRequest(request.queryString).get

        object lookup extends SearchDAO
        for {
          ids <- searchFacets(guide, selectedFacets)
          docs <- lookup.listByGid[DocumentaryUnit](facetSlice(ids, page))
          accessPoints <- lookup.list[AnyModel](selectedFacets)
        } yield Ok(p.guides.facet(pagify(ids, docs, accessPoints, page), accessPoints, GuidePage.faceted -> (guide -> guide.findPages)))
        //} yield Ok()
      } else {
        immediate(Ok(p.guides.facet(ItemPage(Seq(), 0,0,0, List()), List(), GuidePage.faceted -> (guide -> guide.findPages))))
      }
    } getOrElse {
      immediate(NotFound(views.html.errors.pageNotFound()))
    }
  }
}