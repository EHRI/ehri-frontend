package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import backend.BackendContentType
import models.UserProfile
import play.api.mvc.{ActionTransformer, Request, WrappedRequest}
import utils.search._

import scala.concurrent.Future


/**
 * Helpers for using the search engine to search a specific
 * type of item.
 */
trait SearchType[MT] extends Read[MT] with Search {

  case class SearchTypeRequest[A](
    result: SearchResult[(MT,SearchHit)],
    userOpt: Option[UserProfile], request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def SearchTypeAction(
      filters: Map[String, Any] = Map.empty,
      extra: Map[String, Any] = Map.empty,
      facetBuilder: FacetBuilder = emptyFacets)(implicit ct: BackendContentType[MT]) =
    OptionalUserAction andThen new ActionTransformer[OptionalUserRequest, SearchTypeRequest] {
      override protected def transform[A](request: OptionalUserRequest[A]): Future[SearchTypeRequest[A]] = {
        implicit val r = request
        findType[MT](
          filters,
          extra,
          facetBuilder = facetBuilder).map { result =>
          SearchTypeRequest(result, request.userOpt, request)
        }
      }
    }
}