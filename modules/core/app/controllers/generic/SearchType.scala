package controllers.generic

import backend.ContentType
import models.UserProfile
import play.api.mvc.{ActionBuilder, ActionTransformer, Request, WrappedRequest}
import utils.PageParams
import utils.search._

import scala.concurrent.Future


/**
  * Helpers for using the search engine to search a specific
  * type of item.
  */
trait SearchType[MT] extends Read[MT] with Search {

  case class SearchTypeRequest[A](
    result: SearchResult[(MT, SearchHit)],
    userOpt: Option[UserProfile], request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def SearchTypeAction(
    params: SearchParams,
    paging: PageParams,
    filters: Map[String, Any] = Map.empty,
    extra: Map[String, Any] = Map.empty,
    sort: SearchSort.Value = SearchSort.DateNewest,
    facetBuilder: FacetBuilder = emptyFacets)(implicit ct: ContentType[MT]): ActionBuilder[SearchTypeRequest] =
    OptionalUserAction andThen new ActionTransformer[OptionalUserRequest, SearchTypeRequest] {
      override protected def transform[A](request: OptionalUserRequest[A]): Future[SearchTypeRequest[A]] = {
        implicit val r = request
        findType[MT](params, paging, filters, extra, sort, facetBuilder = facetBuilder).map { result =>
          SearchTypeRequest(result, request.userOpt, request)
        }
      }
    }
}