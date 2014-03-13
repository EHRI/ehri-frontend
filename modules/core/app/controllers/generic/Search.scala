package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import defines.EntityType
import models.json.{ClientConvertable, RestReadable}
import play.api.libs.json.Json
import utils.search._
import play.api.Logger
import play.api.i18n.Lang
import controllers.base.{ControllerHelpers, AuthController}
import backend.rest.SearchDAO


/**
 * Controller trait searching via the Solr interface. Eventually
 * we should try and genericise this so it's not tied to Solr.
 */
trait Search extends Controller with AuthController with ControllerHelpers {

  def searchDispatcher: utils.search.Dispatcher
  def searchResolver: utils.search.Resolver

  type FacetBuilder = RequestHeader => FacetClassList
  private val emptyFacets: FacetBuilder = { lang => List.empty[FacetClass[Facet]] }

  case class SearchConfiguration(
    defaultParams: Option[SearchParams] = None,
    facetBuilder: FacetBuilder = emptyFacets,
    searchMode: SearchMode.Value = SearchMode.DefaultAll,
    filters: Map[String,Any] = Map.empty
  )

  def bindFacetsFromRequest(facetClasses: FacetClassList)(implicit request: Request[AnyContent]): List[AppliedFacet] = {
    val qs = request.queryString
    facetClasses.flatMap { fc =>
      qs.get(fc.param).map(_.filterNot(_.trim.isEmpty)).map { values =>
        AppliedFacet(fc.key, values.toList)
      }
    }
  }

  /**
   * Search sort logic. By default, if there's a query, items come out
   * sorted by their score. Otherwise, they are sorted by name.
   */
  type SortFunction = (SearchParams => Request[_]) => Option[SearchOrder.Value]
  private def defaultSortFunction(sp: SearchParams, request: Request[_]): Option[SearchOrder.Value] = {
    if (sp.sort.isDefined) sp.sort
    else {
      val q = request.getQueryString(SearchParams.QUERY)
      if (q.exists(!_.trim.isEmpty)) Some(SearchOrder.Score)
      else Some(SearchOrder.Name)
    }
  }

  /**
   * Action that restricts the search to the inherited entity type
   * and applies.
   */
  def searchAction[MT](filters: Map[String,Any] = Map.empty, defaultParams: Option[SearchParams] = None,
                        entityFacets: FacetBuilder = emptyFacets, mode: SearchMode.Value = SearchMode.DefaultAll)(
      f: ItemPage[(MT, SearchHit)] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = defaultParams.map( p => p.copy(sort = defaultSortFunction(p, request)))

      // Override the entity type with the controller entity type
      val sp = SearchParams.form.bindFromRequest
          .value.getOrElse(SearchParams())
          .setDefault(params)

      val allFacets = entityFacets(request)
      val facets: List[AppliedFacet] = bindFacetsFromRequest(allFacets)
      searchDispatcher.search(sp, facets, allFacets, filters, mode).flatMap { res =>
        val ids = res.items.map(_.id)
        searchResolver.resolve[MT](res.items).map { list =>
          if (list.size != ids.size) {
            Logger.logger.warn("Items returned by search were not found in database: {} -> {}",
              (ids, list))
          }
          val page = res.copy(items = list.zip(res.items))
          render {
            case Accepts.Json() | Accepts.JavaScript() => Ok(Json.obj(
              "page" -> Json.toJson(res.copy(items = list))(ItemPage.itemPageWrites),
              "params" -> Json.toJson(sp)(SearchParams.Converter.clientFormat),
              "appliedFacets" -> Json.toJson(facets)
            )).as(play.api.http.ContentTypes.JSON)
            case _ => f(page)(sp)(facets)(userOpt)(request)
          }
        }
      }
    }
  }

  def filterAction(filters: Map[String,Any] = Map.empty, defaultParams: Option[SearchParams] = None)(
        f: ItemPage[(String,String,EntityType.Value)] => Option[UserProfile] => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
    userProfileAction.async { implicit userOpt => implicit request =>

      val params = defaultParams.map( p => p.copy(sort = defaultSortFunction(p, request)))
      // Override the entity type with the controller entity type
      val sp = SearchParams.form.bindFromRequest
        .value.getOrElse(SearchParams())
        .setDefault(params)

    println(sp)
      searchDispatcher.filter(sp, filters).map { res =>
        f(res)(userOpt)(request)
      }
    }
  }
}