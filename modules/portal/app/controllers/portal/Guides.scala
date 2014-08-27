package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.generic.Search
import play.api.mvc._
import views.html.p

import utils._
import utils.search._
import defines.EntityType

import backend.Backend
import backend.rest.{SearchDAO, RestBackend}
import backend.rest.cypher.CypherDAO

import controllers.base.{SessionPreferences, ControllerHelpers}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

import models._
import models.{Guide, GuidePage, GeoCoordinates}
import models.GuidePage.Layout
import models.base.AnyModel
import utils.search.{Facet, FacetClass}
import play.api.libs.json.{Json, JsString, JsValue, JsNumber, JsNull}

import com.google.inject._
import play.api.Play.current
import solr.SolrConstants

import play.api.data._
import play.api.data.Forms._

case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountDAO)
  extends ControllerHelpers
  with Search
  with FacetConfig
  with PortalBase
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs
  val ajaxOrder = utils.search.SearchOrder.Name
  val htmlOrder = utils.search.SearchOrder.Detail
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
  def getParams(request: Request[Any], eT: EntityType.Value, sort: Option[utils.search.SearchOrder.Value]): SearchParams = { 
    request.getQueryString("parent") match {
      case Some(parent) => SearchParams(query = Some(SolrConstants.PARENT_ID + ":" + parent), entities = List(eT), sort = sort)
      case _ => SearchParams(query = Some(SolrConstants.TOP_LEVEL + ":" + true), entities = List(eT), sort = sort)
    }
  }

  /*
  * Return Map extras param if needed
  */
  def mapParams(request: Map[String,Seq[String]]): (utils.search.SearchOrder.Value, Map[String, Any]) = {
    GeoCoordinates.form.bindFromRequest(request).fold(
      errorForm => {
        SearchOrder.Name -> Map.empty
      },
      latlng => { 
        SearchOrder.Location -> Map("pt" -> latlng.toString, "sfield" -> "location", "sort" -> "geodist() asc")
      }
    )
  }

  def returnJson(hits:utils.search.ItemPage[(models.Concept, utils.search.SearchHit)], linksCount: Map[String, Long]): List[JsValue] = {

      hits.items.map { case(item, id) =>
        item.descriptions.map { case(desc) =>
          Json.toJson(Map(
            "id" -> item.id,
            "latitude" -> desc.latitude.getOrElse(None).toString,
            "longitude" -> desc.longitude.getOrElse(None).toString,
            "name" -> desc.name.toString,
            "links" -> linksCount.get(item.id).getOrElse(0).toString
          ))
        }.toList
      }.toList.flatten
  }


  /*
   *    Count Links by items
   */
  def countLinks(virtualUnit: String, target: List[String]): Future[Map[String, Long]] = {
    if(target.length > 0){
        val cypher = new CypherDAO
        val query =  s"""
          START 
            virtualUnit = node:entities(__ID__= {inContext}), 
            accessPoints = node:entities({accessPoint})
          MATCH 
               (link)-[:inContextOf]->virtualUnit,
              (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
           WHERE doc <> accessPoints
           RETURN accessPoints.__ID__, COUNT(ID(doc))
          """.stripMargin
          val params =  Map(
            "inContext" -> JsString(virtualUnit),
            "accessPoint" -> JsString(getFacetQuery(target))
          )
          cypher.cypher(query, params).map { json =>
            (json \ "data").as[List[List[JsValue]]].flatMap {
              case JsString(id) :: JsNumber(count) :: _ => Some(id -> count.toLong)
              case _ => None
            }.toMap
          }
      } else {
        Future.successful(Map.empty[String,Long])
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
   *    Return Ajax 
   */

  def guideJson(item: AnyModel, count: Long = 0):JsValue = {
    item match {
      case it:HistoricalAgent => {
          Json.obj(
            "name" -> Json.toJson(it.toStringLang),
            "id" -> Json.toJson(it.id),
            "type" -> Json.toJson("historicalAgent"),
            "links" -> Json.toJson(count),
            "descriptions" -> Json.toJson(it.descriptions.map { case (desc) =>
                  Json.obj(
                    "datesOfExistence"-> Json.toJson(desc.details.datesOfExistence),
                    "history"-> Json.toJson(desc.details.history),
                    "places"-> Json.toJson(desc.details.places),
                    "legalStatus"-> Json.toJson(desc.details.legalStatus),
                    "functions"-> Json.toJson(desc.details.functions),
                    "mandates"-> Json.toJson(desc.details.mandates),
                    "internalStructure"-> Json.toJson(desc.details.internalStructure),
                    "generalContext"-> Json.toJson(desc.details.generalContext)
                  )
              })
          )
      }
      case it:Concept => {
          Json.obj(
            "name" -> Json.toJson(it.toStringLang),
            "id" -> Json.toJson(it.id),
            "type" -> Json.toJson("cvocConcept"),
            "links" -> Json.toJson(count),
            "parent" -> Json.toJson(it.parent match {
                case Some(p) => Json.obj(
                    "name" -> Json.toJson( p.toStringLang),
                    "id" -> Json.toJson(p.id)
                  )
                case _ => JsNull
              }),
            "descriptions" -> Json.toJson(it.descriptions.map { case (desc) =>
                Json.toJson(Map(
                    "definition" -> Json.toJson(desc.definition),
                    "scopeNote" -> Json.toJson(desc.scopeNote),
                    "longitude" -> Json.toJson(desc.longitude),
                    "latitude" -> Json.toJson(desc.latitude)
                ))
              }.toList)
          )
      }
      case _ => JsNull
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
        case Layout.Person => guideAuthority(page, Map(SolrConstants.HOLDER_ID -> page.content), guide)
        case Layout.Map => guideMap(page, Map(SolrConstants.HOLDER_ID -> page.content), guide)
        case Layout.Organisation => guideOrganization(page, Map(SolrConstants.HOLDER_ID -> page.content), guide)
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
     for { 
          r <- find[HistoricalAgent](filters = params, defaultParams = SearchParams(sort = Some(if(isAjax) ajaxOrder else htmlOrder)), entities = List(EntityType.HistoricalAgent))
          links <- countLinks(guide.virtualUnit, r.page.items.map { case(item, hit) => item.id }.toList)
      }
      yield {
          render {
            case Accepts.Html() => {
              if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params, links))
              else Ok(p.guides.person(template -> (guide -> guide.findPages), r.page, r.params, links))
            }
            case Accepts.Json() => {
              Ok(Json.toJson(r.page.items.map { case (agent, hit) =>
                  guideJson(agent, links.get(agent.id).getOrElse(0))
                }))
            }
          }
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
      for {
        r <- find[Concept](params, extra = geoloc, defaultParams = SearchParams(entities = List(EntityType.Concept),sort = Some(sort),count = 50), entities = List(EntityType.Concept), facetBuilder = conceptFacets)
        links <- countLinks(guide.virtualUnit, r.page.items.map { case(item, hit) => item.id }.toList)
      }
      yield {
          render {
            case Accepts.Html() => {
              if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params, links))
              else Ok(p.guides.places(template -> (guide -> guide.findPages), r.page, r.params, links, Json.toJson(returnJson(r.page, links))))
            }
            case Accepts.Json() => {
              Ok(Json.toJson(r.page.items.map { case (concept, hit) =>
                  guideJson(concept, links.get(concept.id).getOrElse(0))
                }))
            }
          }
      }
    }
  }

  /*
  *   Layout named "organisation" [Concept]
  */
  def guideOrganization(template: GuidePage, params: Map[String, String], guide: Guide) = userBrowseAction.async { implicit userDetails => implicit request =>
     for { 
        r <- find[Concept](params, defaultParams = getParams(request, EntityType.Concept, Some(if(isAjax) ajaxOrder else htmlOrder)), facetBuilder = conceptFacets)
        links <- countLinks(guide.virtualUnit, r.page.items.map { case(item, hit) => item.id }.toList)
      }
      yield {
          render {
            case Accepts.Html() => {
                if (isAjax) Ok(p.guides.ajax(template -> guide, r.page, r.params, links))
                else Ok(p.guides.organisation(template -> (guide -> guide.findPages), r.page, r.params, links))
            }
            case Accepts.Json() => {
              Ok(Json.toJson(r.page.items.map { case (concept, hit) =>
                  guideJson(concept, links.get(concept.id).getOrElse(0))
                }))
            }
          }
    }
  }

  /*
  *   Layout named "md" [Markdown]
  */
  def guideMarkdown(template: GuidePage, content: String, guide: Guide) = userProfileAction { implicit userOpt => implicit request =>
    Ok(p.guides.markdown(template -> (guide -> guide.findPages), content))
  }


  /*
  *  Layout named "document"
  */
    def browseDocument(path: String, id: String) = getAction[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
        item => details => implicit userOpt => implicit request =>
      itemOr404(Guide.find(path, activeOnly = true)) { guide =>
        Ok(p.guides.documentaryUnit(
          item,
          details.annotations,
          details.links,
          details.watched,
          GuidePage.document(Some(item.toStringLang)) -> (guide -> guide.findPages))
        )
      }
    }

    /*
     * Function for displaying repository
     */

     def browseRepository(path: String, id: String) = getAction[Repository](EntityType.Repository, id) {
        item => details => implicit userOpt => implicit request =>
        itemOr404(Guide.find(path, activeOnly = true)) { guide => 
          Ok(p.guides.repository(item, GuidePage.repository(Some(item.toStringLang)) -> (guide -> guide.findPages)))
        }
    }

  /*
  *
  * Ajax functionnalities for guides
  *
      $.post(
      "http://localhost:9000/guides/:guide/:page",
      {},
      function(data) {},
      "html")
  *
  *
  */

/*********************************************
 *
 *        FACETED SEARCH PART
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
          accessPoints = node:entities({guideFacets})
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
      "accesslist" -> Json.toJson(ids),
      "guideFacets" -> JsString(getFacetQuery(ids))
      /* End IDS */
    )).map { r =>
      (r \ "data").as[Seq[Seq[Long]]].flatten
    }
  }

  /* Function to get items*/
  def otherFacets(guide: Guide, ids: Seq[Long]): Future[Seq[Long]] = {
    println(ids)
    val cypher = new CypherDAO
    val query = 
    s"""
        START 
          virtualUnit = node:entities(__ID__= {guide}), 
          doc = node({docList})
        MATCH 
             (link)-[:inContextOf]->virtualUnit,
            doc<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->(accessPoints)
          WHERE doc <> accessPoints
         RETURN DISTINCT ID(accessPoints)
        """.stripMargin
    cypher.cypher(query, 
    Map(
      /* All IDS */
      "guide" -> JsString(guide.virtualUnit),
      "docList" -> Json.toJson(ids)
      /* End IDS */
    )).map { r =>
      (r \ "data").as[Seq[Seq[Long]]].flatten
    }
  }

  /*
   *    Page defintion
   */
  def facetPage(count: Int, page:Option[Int]) : (Int, Int) = {
    val start = (page.getOrElse(1) - 1) * 10

    if(start > count) {
      val begin = count / 10 * 10 
      val end = (count / 10 * 10) + 10
      (begin, end)
    } else {
      val begin = if(start < 0) 0 else start
      val end = if(start < 10) 10 else if(start+10 > count)  count else start + 10
      (begin, end)
    }
  }

  def facetSlice(ids : Seq[Long], page:Option[Int]) : Seq[Long] = {
    val pages = facetPage(ids.size, page)
    ids.slice(pages._1, pages._2)
  }

  case class GuideFacet(value : String, name : Option[String], applied : Boolean, count : Int) extends Facet
  case class GuideFacetClass(
    param: String = "kw[]",
    name: String = "Keyword",
    key: String = "kw",
    display: FacetDisplay.Value = FacetDisplay.List,
    sort:FacetSort.Value = FacetSort.Fixed,
    fieldType: String = "neo4j",
    facets: List[GuideFacet]
  ) extends FacetClass[GuideFacet] {
    def render = (s : String) => s
  }


  def pagify(docsId : Seq[Long], docsItems: Seq[DocumentaryUnit], accessPoints: Seq[AnyModel], page: Option[Int] = None): ItemPage[DocumentaryUnit] = {
    facetPage(docsId.size, page) match { 
      case (start, end) => ItemPage(
        items = docsItems,
        page = start,
        count = end - start,
        total = docsId.size,
        facets = List(
          GuideFacetClass(
            facets = accessPoints.map { ap =>
              GuideFacet(value = ap.id, name = Some(ap.toStringLang), applied = true, count = 1)
            }.toList
          )
        )
      )
    }
  }

  def mapAccessPoints(guide: Guide, facets: Seq[AnyModel]): Map[String, List[AnyModel]] = {
    guide.findPages().map( page => 
        page.content -> facets.map { facet =>
          facet match {
            case f:Concept => {
              f.vocabulary match {
                case Some(s:Vocabulary) => if(s.id == page.content) { facet }  else None
                case _ => None
              }
              
            }
            case f:HistoricalAgent => {
              f.set match {
                case Some(s:AuthoritativeSet) => if(s.id == page.content) { facet }  else None
                case _ => None
              }
            }
            case _ => None
          }
        }.flatMap {
          case f:AnyModel => Some(f)
          case _ => None
        }.toList
      ).toMap
  }

  /*
  *   Faceted search
  */
  def guideFacets(path: String) = userProfileAction.async { implicit userOpt => implicit request =>
    Guide.find(path, activeOnly = true).map { guide =>
      /*
       *  If we have keyword, we make a query 
       */
      val defaultResult = Ok(p.guides.facet(ItemPage(Seq(), 0, 0, 0, List()), Map().empty, GuidePage.faceted -> (guide -> guide.findPages)))
      facetsForm.bindFromRequest(request.queryString).fold(
        errs => immediate(defaultResult), {
          case (selectedFacets, page) if !selectedFacets.isEmpty => for {
            ids <- searchFacets(guide, selectedFacets)
            docs <- SearchDAO.listByGid[DocumentaryUnit](facetSlice(ids, page))
            selectedAccessPoints <- SearchDAO.list[AnyModel](selectedFacets)
            availableFacets <- otherFacets(guide, ids)
            tempAccessPoints <- SearchDAO.listByGid[AnyModel](availableFacets)
          } yield Ok(p.guides.facet(pagify(ids, docs, selectedAccessPoints, page), mapAccessPoints(guide, tempAccessPoints), GuidePage.faceted -> (guide -> guide.findPages)))
          case _ => immediate(defaultResult)
        }
      )
    } getOrElse {
      immediate(NotFound(views.html.errors.pageNotFound()))
    }
  }

}