package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import defines.EntityType
import models.json.{ClientConvertable, RestReadable}
import play.api.libs.json.Json
import utils.search._
import play.api.Logger
import play.api.i18n.Lang


/**
 * Controller trait searching via the Solr interface. Eventually
 * we should try and genericise this so it's not tied to Solr.
 */
trait EntitySearch extends Controller with AuthController with ControllerHelpers {

  def searchDispatcher: utils.search.Dispatcher

  type FacetBuilder = Lang => FacetClassList
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
      if (q.map(!_.trim.isEmpty).getOrElse(false))
        Some(SearchOrder.Score)
      else Some(SearchOrder.Name)
    }
  }

  /**
   * Action that restricts the search to the inherited entity type
   * and applies.
   */
  def searchAction[MT](filters: Map[String,Any] = Map.empty, defaultParams: Option[SearchParams] = None,
                        entityFacets: FacetBuilder = emptyFacets, mode: SearchMode.Value = SearchMode.DefaultAll)(
      f: ItemPage[(MT, String)] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = defaultParams.map( p => p.copy(sort = defaultSortFunction(p, request)))

      // Override the entity type with the controller entity type
      val sp = SearchParams.form.bindFromRequest
          .value.getOrElse(SearchParams())
          .setDefault(params)

      val allFacets = entityFacets(lang)
      val facets: List[AppliedFacet] = bindFacetsFromRequest(allFacets)

      searchDispatcher.search(sp, facets, allFacets, filters, mode).flatMap { res =>
        val ids = res.items.map(_.id)
        val itemIds = res.items.map(_.itemId)
        rest.SearchDAO(userOpt).list[MT](itemIds).map { list =>
          if (list.size != ids.size) {
            Logger.logger.warn("Items returned by search were not found in database: {} -> {}",
              (ids, list))
          }
          val page = res.copy(items = list.zip(ids))
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

      searchDispatcher.filter(sp, filters).map { res =>
        f(res)(userOpt)(request)
      }
    }
  }
}