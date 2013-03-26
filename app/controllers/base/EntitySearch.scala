package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import models.{Entity, UserProfile}
import solr.facet.{AppliedFacet, FacetClass}
import solr.SearchParams
import defines.EntityType

/**
 * Controller trait searching via the Solr interface. Eventually
 * we should try and genericise this so it's not tied to Solr.
 */
trait EntitySearch extends Controller with AuthController with ControllerHelpers {

  /**
   * Inheriting controllers should override a list of facets that
   * are available for the given entity type.
   */
  val entityFacets: List[FacetClass]
  val searchEntities: List[EntityType.Value]

  def bindFacetsFromRequest(facetClasses: List[FacetClass])(implicit request: Request[AnyContent]): List[AppliedFacet] = {
    val qs = request.queryString
    facetClasses.flatMap { fc =>
      qs.get(fc.param).map { values =>
        AppliedFacet(fc.key, values.toList)
      }
    }
  }


  /**
   * Short cut search action without the ability to provide default filters
   * @param f
   * @return
   */
  def searchAction(f: solr.ItemPage[Entity] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    searchAction(Map.empty[String,String])(f)
  }

  /**
   * Action that restricts the search to the inherited entity type
   * and applies
   * @param f
   * @return
   */
  def searchAction(filters: Map[String,String] = Map.empty)(
      f: solr.ItemPage[Entity] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    userProfileAction { implicit userOpt => implicit request =>
      //Secured {
        // Override the entity type with the controller entity type
        val sp = solr.SearchParams.form.bindFromRequest.value.get.copy(entities = searchEntities)
        val facets: List[AppliedFacet] = bindFacetsFromRequest(entityFacets)
        AsyncRest {
          solr.SolrDispatcher(userOpt).list(sp, facets, entityFacets).map { resOrErr =>
            resOrErr.right.map { res =>
              AsyncRest {
                rest.SearchDAO(userOpt).list(res.items.map(_.id)).map { listOrErr =>
                  listOrErr.right.map { list =>
                    f(res.copy(items = list))(sp)(facets)(userOpt)(request)
                  }
                }
              }
            }
          }
        }
      //}
    }
  }
}