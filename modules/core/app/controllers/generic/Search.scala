package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import defines.EntityType
import utils.search._
import play.api.Logger
import controllers.base.{ControllerHelpers, AuthController}
import scala.concurrent.Future
import backend.BackendReadable


/**
 * Controller trait searching via the Solr interface. Eventually
 * we should try and genericise this so it's not tied to Solr.
 */
trait Search extends Controller with AuthController with ControllerHelpers {

  def searchDispatcher: utils.search.Dispatcher
  def searchResolver: utils.search.Resolver

  type FacetBuilder = RequestHeader => FacetClassList
  protected val emptyFacets: FacetBuilder = { lang => List.empty[FacetClass[Facet]]}

  def bindFacetsFromRequest(facetClasses: FacetClassList)(implicit request: RequestHeader): List[AppliedFacet] = {
    facetClasses.flatMap { fc =>
      request.queryString.get(fc.param).map(_.filterNot(_.trim.isEmpty)).map { values =>
        AppliedFacet(fc.key, values.toList)
      }
    }
  }

  /**
   * Search sort logic. By default, if there's a query, items come out
   * sorted by their score. Otherwise, they are sorted by name.
   */
  type SortFunction = (SearchParams => RequestHeader) => Option[SearchOrder.Value]

  private def defaultSortFunction(sp: SearchParams, request: RequestHeader): Option[SearchOrder.Value] = {
    if (sp.sort.isDefined) sp.sort
    else Some {
      if (request.getQueryString(SearchParams.QUERY).exists(!_.trim.isEmpty)) SearchOrder.Score
      else SearchOrder.DateNewest
    }
  }

  /**
   * Dispatch a search to the search engine.
   *
   * @param filters A map of key/value filter pairs
   * @param extra An arbitrary set of key/value parameters
   * @param defaultParams The default parameters
   * @param entities A list of entities to limit the search to
   * @param facetBuilder A function to create the set of facets
   *                     from the incoming request
   * @param mode The search mode, default all or default to none
   * @return A query result containing the page of search data,
   *         plus the resolved parameters and facets.
   */
  def find[MT](filters: Map[String, Any] = Map.empty,
               extra: Map[String, Any] = Map.empty,
               defaultParams: SearchParams = SearchParams.empty,
               entities: Seq[EntityType.Value] = Nil,
               facetBuilder: FacetBuilder = emptyFacets,
               mode: SearchMode.Value = SearchMode.DefaultAll)(
                implicit request: RequestHeader, userOpt: Option[UserProfile], rd: BackendReadable[MT]): Future[QueryResult[MT]] = {

    val params = defaultParams
      .copy(sort = defaultSortFunction(defaultParams, request))
      .copy(entities = if (entities.isEmpty) defaultParams.entities else entities.toList)

      val sp = SearchParams.form.bindFromRequest(request.queryString)
        .value.getOrElse(SearchParams.empty)
        .setDefault(Some(params), Some(request.queryString))

    val allFacets = facetBuilder(request)
    val boundFacets: List[AppliedFacet] = bindFacetsFromRequest(allFacets)

    for {
      res <- searchDispatcher.search(sp, boundFacets, allFacets, filters, extra, mode)
      list <- searchResolver.resolve[MT](res.items)
    } yield {
      if (list.size != res.size) {
        Logger.logger.warn("Items returned by search were not found in database: {} -> {}",
          (res.items.map(_.id), list))
      }
      QueryResult(res.copy(items = list.zip(res.items)), sp, boundFacets)
    }
  }

  case class QueryResult[MT](
    page: ItemPage[(MT, SearchHit)] = ItemPage.empty,
    params: SearchParams = SearchParams.empty,
    facets: List[AppliedFacet] = Nil
  )

  object QueryResult {
    def empty[T]: QueryResult[T] = QueryResult(ItemPage.empty, SearchParams.empty, List.empty)
  }

  /**
   * Action that restricts the search to the inherited entity type
   * and applies.
   */
  def searchAction[MT](filters: Map[String, Any] = Map.empty,
                       extra: Map[String, Any] = Map.empty,
                       defaultParams: SearchParams = SearchParams.empty,
                       entities: Seq[EntityType.Value] = Nil,
                       entityFacets: FacetBuilder = emptyFacets,
                       mode: SearchMode.Value = SearchMode.DefaultAll)(
                        f: ItemPage[(MT, SearchHit)] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT]): Action[AnyContent] = {
    userProfileAction.async { implicit userOpt => implicit request =>
      find[MT](
        filters,
        extra,
        defaultParams,
        entities,
        entityFacets,
        mode
      ).map { r =>
        f(r.page)(r.params)(r.facets)(userOpt)(request)
      }
    }
  }

  def filterAction(filters: Map[String, Any] = Map.empty, defaultParams: Option[SearchParams] = None)(
    f: ItemPage[FilterHit] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = defaultParams.map(p => p.copy(sort = defaultSortFunction(p, request)))
      // Override the entity type with the controller entity type
      val sp = SearchParams.form.bindFromRequest
        .value.getOrElse(SearchParams.empty)
        .setDefault(params)

      searchDispatcher.filter(sp, filters).map { res =>
        f(res)(userOpt)(request)
      }
    }
  }
}