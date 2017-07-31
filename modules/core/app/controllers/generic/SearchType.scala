package controllers.generic

import models.UserProfile
import play.api.mvc._
import services.data.ContentType
import utils.PageParams
import services.search._

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
    facetBuilder: FacetBuilder = emptyFacets)(implicit ct: ContentType[MT]): ActionBuilder[SearchTypeRequest, AnyContent] =
    OptionalUserAction andThen new CoreActionTransformer[OptionalUserRequest, SearchTypeRequest] {
      override protected def transform[A](request: OptionalUserRequest[A]): Future[SearchTypeRequest[A]] = {
        implicit val r = request
        findType[MT](params, paging, filters, extra, sort, facetBuilder = facetBuilder).map { result =>
          SearchTypeRequest(result, request.userOpt, request)
        }
      }
    }
}