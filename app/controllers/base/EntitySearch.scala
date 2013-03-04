package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import defines.PermissionType
import models.{Entity, UserProfile}
import solr.facet.{AppliedFacet, FacetClass, FacetData}
import solr.SearchParams

/**
 * Controller trait for deleting AccessibleEntities.
 *
 * @tparam T the Entity's built representation
 */
trait EntitySearch[T <: AccessibleEntity] extends EntityRead[T] {

  /**
   * Inheriting controllers should override a list of facets that
   * are available for the given entity type.
   */
  val entityFacets: List[FacetClass]

  /**
   * Action that restricts the search to the inherited entity type
   * and applies
   * @param f
   * @return
   */
  def searchAction(f: solr.ItemPage[Entity] => SearchParams => List[AppliedFacet] => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction { implicit userOpt => implicit request =>

      val sp = solr.SearchParams.form.bindFromRequest.value.get.copy(entity = Some(entityType))
      val facets: List[AppliedFacet] = FacetData.bindFromRequest(entityFacets)
      AsyncRest {
        solr.SolrDispatcher(userOpt).list(sp, facets).map { resOrErr =>
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
    }
  }
}