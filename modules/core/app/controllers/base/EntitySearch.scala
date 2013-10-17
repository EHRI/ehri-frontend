package controllers.base

import global.GlobalConfig
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.UserProfile
import defines.EntityType
import play.api.Play._
import models.json.{ClientConvertable, RestReadable}
import play.api.libs.json.Json
import utils.search._
import play.api.Logger


/**
 * Controller trait searching via the Solr interface. Eventually
 * we should try and genericise this so it's not tied to Solr.
 */
trait EntitySearch extends Controller with AuthController with ControllerHelpers {

  def searchDispatcher: utils.search.Dispatcher

  def bindFacetsFromRequest(facetClasses: List[FacetClass[Facet]])(implicit request: Request[AnyContent]): List[AppliedFacet] = {
    val qs = request.queryString
    facetClasses.flatMap { fc =>
      qs.get(fc.param).map { values =>
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
   * Short cut search action without the ability to provide default filters
   * @param f
   * @return
   */
  def searchAction[MT](f: ItemPage[(MT,String)] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
    searchAction[MT](Map.empty[String,Any])(f)
  }

  /**
   * Action that restricts the search to the inherited entity type
   * and applies
   * @param f
   * @return
   */
  def searchAction[MT](filters: Map[String,Any] = Map.empty, defaultParams: Option[SearchParams] = None,
                        entityFacets: FacetClassList = Nil, mode: SearchMode.Value = SearchMode.DefaultAll)(
      f: ItemPage[(MT, String)] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
    userProfileAction { implicit userOpt => implicit request =>
      val params = defaultParams.map( p => p.copy(sort = defaultSortFunction(p, request)))

      // Override the entity type with the controller entity type
      val sp = SearchParams.form.bindFromRequest
          .value.getOrElse(SearchParams())
          .setDefault(params)

      val facets: List[AppliedFacet] = bindFacetsFromRequest(entityFacets)
      AsyncRest {
        searchDispatcher.search(sp, facets, entityFacets, filters, mode).map { resOrErr =>
          resOrErr.right.map { res =>
            val ids = res.items.map(_.id)
            val itemIds = res.items.map(_.itemId)
            AsyncRest {
              rest.SearchDAO(userOpt).list[MT](itemIds).map { listOrErr =>
                listOrErr.right.map { list =>
                  // Sanity check!
                  if (list.size != ids.size) {
                    Logger.logger.warn("Items returned by search were not found in database: {} -> {}",
                      (ids, list))
                  }
                  val page = res.copy(items = list.zip(ids))
                  f(page)(sp)(facets)(userOpt)(request)
                }
              }
            }
          }
        }
      }
    }
  }

  def filterAction(filters: Map[String,Any] = Map.empty, defaultParams: Option[SearchParams] = None)(
        f: ItemPage[(String,String,EntityType.Value)] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit userOpt => implicit request =>

      val params = defaultParams.map( p => p.copy(sort = defaultSortFunction(p, request)))
      // Override the entity type with the controller entity type
      val sp = SearchParams.form.bindFromRequest
        .value.getOrElse(SearchParams())
        .setDefault(params)

      AsyncRest {
        searchDispatcher.filter(sp, filters).map { resOrErr =>
          resOrErr.right.map { res =>
            f(res)(userOpt)(request)
          }
        }
      }
    }
  }
}