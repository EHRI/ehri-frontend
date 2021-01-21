package controllers.portal.guides

import javax.inject._
import services.cypher.CypherService
import controllers.base.SearchVC
import controllers.generic.Search
import controllers.portal.FacetConfig
import controllers.portal.base.PortalController
import controllers.{AppComponents, renderError}
import defines.EntityType
import models.GuidePage.Layout
import models.base.Model
import models.{GeoCoordinates, Guide, GuidePage, _}
import play.api.cache.Cached
import play.api.data.Forms._
import play.api.data._
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc._
import utils.PageParams
import services.search._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Guides @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  guides: GuideService,
  cypher: CypherService,
  fc: FacetConfig,
  statusCache: Cached,
) extends PortalController
  with Search
  with SearchVC {

  import scala.concurrent.duration._

  private val ajaxOrder = services.search.SearchSort.Name
  private val htmlAgentOrder = services.search.SearchSort.Detail
  private val htmlConceptOrder = services.search.SearchSort.ChildCount

  def jsRoutes: EssentialAction = statusCache.status((_: RequestHeader) => "pages:guideJsRoutes", OK, 1.hour) {
    Action { implicit request =>
      Ok(
        play.api.routing.JavaScriptReverseRouter("jsRoutes")(
          controllers.portal.routes.javascript.Portal.filterItems,
          controllers.portal.guides.routes.javascript.DocumentaryUnits.browse,
          controllers.portal.routes.javascript.DocumentaryUnits.browse,
          controllers.portal.guides.routes.javascript.Guides.linkedData,
          controllers.portal.guides.routes.javascript.Guides.linkedDataInContext
        )
      ).as(MimeTypes.JAVASCRIPT)
    }
  }

  /*
  *  Return SearchParams for items with hierarchy
  */
  private def getParams(params: SearchParams, request: Request[Any], et: EntityType.Value, sort: Option[services.search.SearchSort.Value], isAjax: Boolean = false): SearchParams = {
    request.getQueryString("parent").map { parent =>
      params.copy(
        filters = Seq(SearchConstants.PARENT_ID + ":" + parent),
        entities = Seq(et),
        sort = sort
      )
    }.getOrElse {
      params.copy(
        filters = if (!isAjax) Seq(SearchConstants.TOP_LEVEL + ":" + true) else params.filters,
        entities = Seq(et),
        sort = sort
      )
    }
  }

  /*
  * Return Map extras param if needed
  */
  private def mapParams(request: Map[String, Seq[String]]): (services.search.SearchSort.Value, Map[String, Any]) = {
    GeoCoordinates.form.bindFromRequest(request).fold(
      errorForm => SearchSort.Name -> Map.empty,
      {
        case GeoCoordinates(lat, lng, dist) => SearchSort.Location -> Map(
          "pt" -> s"$lat,$lng",
          "sfield" -> "location",
          "sort" -> "geodist() asc",
          "d" -> dist.getOrElse(1000), // km
          "fq" -> "{!bbox}"
        )
      }
    )
  }

  /*
   *    Count Links by items
   */
  private def countLinks(virtualUnit: String, target: Seq[String]): Future[Map[String, Long]] = {
    if (target.nonEmpty) {
      val query =
        s"""
          MATCH
              (vc:VirtualUnit {__id: {inContext}})<-[:inContextOf]-(link:Link),
              (entity:_Entity)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->(doc)
           WHERE entity.__id IN {accessPoints}
             AND doc <> entity
           RETURN entity.__id AS id, COUNT(doc)
          """.stripMargin
      val params = Map(
        "inContext" -> JsString(virtualUnit),
        "accessPoints" -> Json.toJson(target)
      )
      cypher.get(query, params).map {
        _.data.collect {
          case JsString(id) :: JsNumber(count) :: _ => id -> count.toLong
        }.toMap
      }
    } else {
      Future.successful(Map.empty[String, Long])
    }
  }

  /*
  *
  *   Routes functions for normal HTML
  *
  */

  private def pageNotFound = Action { implicit request =>
    NotFound(renderError("errors.pageNotFound", views.html.errors.pageNotFound()))
  }

  private def itemOr404Action(f: => Option[Action[AnyContent]]): Action[AnyContent] = {
    f.getOrElse(pageNotFound)
  }

  /*
  * Return a list of guides
  */
  def listGuides(): Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.guides.guidesList(guides.findAll(activeOnly = true)))
  }

  /*
  * Return a homepage for a guide
  */
  def home(path: String, params: SearchParams, paging: PageParams): Action[AnyContent] = itemOr404Action {
    guides.find(path, activeOnly = true).map { guide =>
      guideLayout(guide, guides.getDefaultPage(guide), params, paging)
    }
  }

  /*
  * Return a layout for a guide and a given path
  */
  def layoutRetrieval(path: String, page: String, params: SearchParams, paging: PageParams): Action[AnyContent] = itemOr404Action {
    guides.find(path, activeOnly = true).map { guide =>
      guideLayout(guide, guides.findPage(guide, page), params, paging)
    }
  }

  /*
   *    Return Ajax 
   */
  private def guideJsonItem(item: Model, count: Long = 0)(implicit requestHeader: RequestHeader): JsValue = {
    item match {
      case it: HistoricalAgent =>
        Json.obj(
          "name" -> it.toStringLang,
          "id" -> it.id,
          "type" -> EntityType.HistoricalAgent,
          "links" -> count
        )
      case it: Concept =>
        Json.obj(
          "name" -> it.toStringLang,
          "id" -> it.id,
          "type" -> EntityType.Concept,
          "links" -> count,
          "childCount" -> Json.toJson(it.childCount.getOrElse(0)),
          "parent" -> Json.toJson(it.parent match {
            case Some(p) => Json.obj(
              "name" -> p.toStringLang,
              "id" -> p.id
            )
            case _ => JsNull
          }),
          "descriptions" -> Json.toJson(it.descriptions.map { desc =>
            Json.toJson(Map(
              ConceptF.DEFINITION -> Json.toJson(desc.definition),
              ConceptF.SCOPENOTE -> Json.toJson(desc.scopeNote),
              ConceptF.LONGITUDE -> Json.toJson(it.data.longitude),
              ConceptF.LATITUDE -> Json.toJson(it.data.latitude)
            ))
          })
        )
      case _ => JsNull
    }
  }

  private def guideJson(page: utils.Page[(Model, services.search.SearchHit)], links: Map[String, Long], pageParam: String = "page")(implicit request: RequestHeader): JsValue = {
    Json.obj(
      "items" -> Json.toJson(page.items.map { case (agent, hit) =>
        guideJsonItem(agent, links.getOrElse(agent.id, 0))
      }),
      "limit" -> JsNumber(page.limit),
      "page" -> JsNumber(page.page),
      "total" -> JsNumber(page.total)
    )
  }

  /*
  *
  * Link a layout [GuidePage] to a correct template function
  *
  */


  def guideLayout(guide: Guide, temp: Option[GuidePage], params: SearchParams, paging: PageParams): Action[AnyContent] =
    itemOr404Action {
      temp.map { page =>
        page.layout match {
          case Layout.Person => guideAuthority(page, Map(SearchConstants.HOLDER_ID -> page.content), guide, params, paging)
          case Layout.Map => guideMap(page, Map(SearchConstants.HOLDER_ID -> page.content), guide, params)
          case Layout.Organisation => guideOrganization(page, Map(SearchConstants.HOLDER_ID -> page.content), guide, params, paging)
          case Layout.Html => guideHtml(guide, page)
          case Layout.Markdown => guideMarkdown(guide, page)
          case Layout.Timeline => guideTimeline(guide, page)
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
  def guideAuthority(page: GuidePage, filters: Map[String, String], guide: Guide, params: SearchParams, paging: PageParams): Action[AnyContent] =
    UserBrowseAction.async { implicit request =>
      for {
        r <- findType[HistoricalAgent](
          params.copy(sort = Some(if (isAjax) ajaxOrder else htmlAgentOrder)), paging, filters)
        links <- countLinks(guide.virtualUnit, r.page.items.map { case (item, hit) => item.id })
      } yield render {
        case Accepts.Html() =>
          if (isAjax) Ok(views.html.guides.ajax(guide, page, r.page, r.params, links))
          else Ok(views.html.guides.person(guide, page, guides.findPages(guide), r.page, r.params, links))
        case Accepts.Json() =>
          Ok(guideJson(r.page, links))
      }
    }

  /*
  *   Layout named "map" [Concept]
  */
  def guideMap(page: GuidePage, filters: Map[String, String], guide: Guide, params: SearchParams): Action[AnyContent] =
    UserBrowseAction.async { implicit request =>
      mapParams(
        if (request.queryString.contains("lat") && request.queryString.contains("lng")) request.queryString
        else page.getParams
      ) match {
        case (sort, geoLocation) => for {
          r <- findType[Concept](params, PageParams(limit = 500), filters,
            geoLocation, sort, facetBuilder = fc.conceptFacets)
          links <- countLinks(guide.virtualUnit, r.page.items.map { case (item, hit) => item.id })
        } yield render {
          case Accepts.Html() =>
            if (isAjax) Ok(views.html.guides.ajax(guide, page, r.page, r.params, links))
            else Ok(views.html.guides.places(guide, page, guides.findPages(guide), r.page, r.params, links, guideJson(r.page, links)))
          case Accepts.Json() =>
            Ok(guideJson(r.page, links))
        }
      }
    }

  /*
   *   Layout named "organisation" [Concept]
   */
  def guideOrganization(page: GuidePage, filters: Map[String, String], guide: Guide, params: SearchParams, paging: PageParams): Action[AnyContent] =
    UserBrowseAction.async { implicit request =>
      val defParams = getParams(params, request, EntityType.Concept,
        Some(if (isAjax) ajaxOrder else htmlConceptOrder), isAjax = isAjax)

      for {
        r <- findType[Concept](defParams, paging, filters, facetBuilder = fc.conceptFacets)
        links <- countLinks(guide.virtualUnit, r.page.items.map { case (item, hit) => item.id })
      } yield render {
        case Accepts.Html() =>
          if (isAjax) Ok(views.html.guides.ajax(guide, page, r.page, r.params, links))
          else Ok(views.html.guides.organisation(guide, page, guides.findPages(guide), r.page, r.params, links))
        case Accepts.Json() =>
          Ok(guideJson(r.page, links))
      }
    }

  /*
   *   Layout named "html" (Html)
   */
  def guideHtml(guide: Guide, page: GuidePage): Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.guides.html(guide, page, guides.findPages(guide)))
  }

  /*
   *   Layout named "html" (Html)
   */
  def guideMarkdown(guide: Guide, page: GuidePage): Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.guides.markdown(guide, page, guides.findPages(guide)))
  }

  /**
    * Layout named "timeline"
    */
  def guideTimeline(guide: Guide, page: GuidePage): Action[AnyContent] = OptionalUserAction { implicit request =>
    Ok(views.html.guides.timeline(guide, page, guides.findPages(guide)))
  }

  def childItemIds(item: String)(implicit request: RequestHeader): Future[Map[String, Any]] = {
    import SearchConstants._
    vcDescendantIds(item).map { seq =>
      if (seq.isEmpty) Map(ITEM_ID -> "__NO_VALID_ID__")
      else Map(s"$ITEM_ID:(${seq.mkString(" ")}) OR $ANCESTOR_IDS:(${seq.mkString(" ")})" -> Unit)
    }
  }

  /*
   *   Faceted request
   */
  private def searchFacets(guide: Guide, ids: Seq[String]): Future[Seq[Long]] = {
    val query =
      s"""
        MATCH
            (vc:VirtualUnit)<-[:inContextOf]-(link: Link),
            (doc:DocumentaryUnit)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->(entity:_Entity)
         WHERE entity.__id IN {accessList} AND vc.__id = {guide}
         WITH collect(entity.__id) AS accessPointsId, doc
         WHERE ALL (x IN {accessList}
                   WHERE x IN accessPointsId)
         RETURN ID(doc)
        """.stripMargin
    cypher.get(query, Map(
      /* All IDS */
      "guide" -> JsString(guide.virtualUnit),
      "accessList" -> Json.toJson(ids)
      /* End IDS */
    )).map(_.data.flatMap(_.map(_.as[Long])))
  }

  /*
   * Function to get items
   */
  private def otherFacets(guide: Guide, ids: Seq[Long]): Future[Seq[Long]] = {
    val query =
      s"""
        START doc = node({docList})
        MATCH 
             (link:Link)-[:inContextOf]->(vc:VirtualUnit),
            (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->(ap:AccessPoint)
          WHERE vc.__id = {guide} AND doc <> ap
         RETURN DISTINCT ID(ap)
        """.stripMargin
    cypher.get(query, Map(
      /* All IDS */
      "guide" -> JsString(guide.virtualUnit),
      "docList" -> Json.toJson(ids)
      /* End IDS */
    )).map(_.data.flatMap(_.map(_.as[Long])))
  }

  private def pagify[T](docs: SearchResult[T], accessPoints: Seq[Model])(implicit requestHeader: RequestHeader): SearchResult[T] = {
    docs.copy(
      facets = docs.facets ++ (if (accessPoints.nonEmpty)
        Seq(AppliedFacet("kw", accessPoints.map(_.id)))
      else Seq.empty),
      facetClasses = docs.facetClasses ++ Seq(
        FieldFacetClass(
          param = "kw",
          name = "Keyword",
          key = "kw",
          render = id => accessPoints.find(_.id == id).map(_.toStringLang).getOrElse(id),
          facets = accessPoints.map { ap =>
            FieldFacet(value = ap.id, name = Some(ap.toStringLang), applied = true, count = 1)
          }
        )
      )
    )
  }

  private def mapAccessPoints(guide: Guide, facets: Seq[Model]): Map[String, Seq[Model]] = {
    guides.findPages(guide).map { page =>
      page.content -> facets.collect {
        case f: Concept if f.vocabulary.exists(_.id == page.content) => f
        case f: HistoricalAgent if f.set.exists(_.id == page.content) => f
      }
    }.toMap
  }

  /*
  *   Faceted search
  */
  def guideFacets(path: String, params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    guides.find(path, activeOnly = true).map { guide =>
      /*
       *  If we have keyword, we make a query 
       */
      val facets = request.queryString.getOrElse("kw", Seq.empty).filter(_.nonEmpty)
      if (facets.isEmpty) {
        for {
          filters <- childItemIds(guide.virtualUnit)
          result <- findType[DocumentaryUnit](params, paging, filters, sort = SearchSort.Name)
        } yield Ok(views.html.guides.facet(
          guide,
          GuidePage.faceted,
          guides.findPages(guide),
          result,
          Map.empty,
          controllers.portal.guides.routes.Guides.guideFacets(path)
        ))
      } else {
        for {
          ids <- searchFacets(guide, facets)
          result <- if (ids.nonEmpty) findType[DocumentaryUnit](params, paging,
            filters = Map(s"gid:(${ids.take(1024).mkString(" ")})" -> Unit),
            sort = SearchSort.Name
          ) else immediate(SearchResult.empty)
          selectedAccessPoints <- userDataApi.fetch[Model](facets)
            .map(_.collect { case Some(h) => h })
          availableFacets <- otherFacets(guide, ids)
          tempAccessPoints <- userDataApi.fetch[Model](gids = availableFacets)
            .map(_.collect { case Some(h) => h })
        } yield {
          Ok(views.html.guides.facet(
            guide,
            GuidePage.faceted,
            guides.findPages(guide),
            pagify(result, selectedAccessPoints),
            mapAccessPoints(guide, tempAccessPoints),
            controllers.portal.guides.routes.Guides.guideFacets(path)
          ))
        }
      }
    } getOrElse {
      immediate(NotFound(renderError("errors.itemNotFound", views.html.errors.itemNotFound(Some(path)))))
    }
  }

  private val searchLinksForm = Form(
    single(
      "type" -> optional(text
        .verifying(
          "NoTypeGiven",
          c => EntityType.values.map(v => v.toString).contains(c)
        )
      )
    )
  )


  private def searchLinks(target: String, documentType: String = EntityType.DocumentaryUnit.toString, context: Option[String] = None): Future[Seq[Long]] = {
    context match {
      case Some(str) =>
        val query =
          s"""
             |MATCH
             |     (link:Link)-[:inContextOf]->(vc:VirtualUnit {__id: {inContext}}),
             |    (doc {__type: {type}})<-[:hasLinkTarget]-(link)
             |       -[:hasLinkTarget]->(entity:_Entity {__id: {accessPoint}})
             |WHERE doc <> entity
             |RETURN ID(doc) LIMIT 5
        """.stripMargin
        val params = Map(
          "inContext" -> JsString(str),
          "accessPoint" -> JsString(target),
          "type" -> JsString(documentType)
        )
        cypher.get(query, params).map(_.data.flatMap(_.map(_.as[Long])))
      case _ =>
        val query: String =
          s"""
             |MATCH
             |     (doc {__type: {type}})<-[:hasLinkTarget]
             |       -(link:Link)-[:hasLinkTarget]->(entity:_Entity {__id:{accessPoint}})
             | WHERE doc <> entity
             | RETURN ID(doc) LIMIT 5
        """.stripMargin
        val params = Map(
          "accessPoint" -> JsString(target),
          "type" -> JsString(documentType)
        )
        cypher.get(query, params).map(_.data.flatMap(_.map(_.as[Long])))
    }
  }

  def linkedData(id: String): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    for {
      gids <- searchLinksForm.bindFromRequest(request.queryString).fold(
        errs => searchLinks(id), {
          case Some(t) => searchLinks(id, t)
          case _ => searchLinks(id)
        })
      docs <- userDataApi.fetch[Model](gids = gids).map(_.collect { case Some(h) => h })
    } yield Ok(Json.toJson(docs.zip(gids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }

  def linkedDataInContext(id: String, context: String): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    for {
      gids <- searchLinksForm.bindFromRequest(request.queryString).fold(
        errs => searchLinks(id, context = Some(context)), {
          case Some(t) => searchLinks(id, t, Some(context))
          case _ => searchLinks(id, context = Some(context))
        })
      docs <- userDataApi.fetch[Model](gids = gids).map(_.collect { case Some(h) => h })
    } yield Ok(Json.toJson(docs.zip(gids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }
}
