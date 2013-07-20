package controllers

import play.api.mvc.{AnyContent, Request}
import play.api.data.Form
import collection.immutable.ListMap


object ListParams {
  final val PAGE = "page"
  final val LIMIT = "limit"
  final val PROPERTY_NAME = "pfn"
  final val PROPERTY_VALUE = "pfv"
  final val SORT = "sort"
  final val REVERSE = "reverse"

  def bind(request: Request[AnyContent]): ListParams = {
    import play.api.data.Forms._
    import rest.RestPageParams._

    // NB: There *should* be no way for the binding
    // of this form to fail, since we have no
    // constraits
    Form(
      mapping(
        PAGE -> optional(number),
        LIMIT -> optional(number),
        PROPERTY_NAME -> list(text),
        PROPERTY_VALUE -> list(text),
        SORT -> optional(nonEmptyText),
        REVERSE -> optional(boolean)
      )(ListParams.apply)(ListParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(new ListParams())
  }
}

/**
 * Class representing the query parameters for a list view.
 * @param page    page number
 * @param limit   per-page limit
 * @param pfn     property filter name
 * @param pfv     property filter value
 * @param sort    sort sort
 */
case class ListParams(
  page: Option[Int] = None,
  limit: Option[Int] = None,
  pfn: List[String] = Nil,
  pfv: List[String] = Nil,
  sort: Option[String] = None, // only allowing one sort here
  reverse: Option[Boolean] = Some(false)
) {

  /**
   * Converts a list param set into a REST param set, given the necessary
   * mappings between our filter and sort values, and the server's filter
   * and sort specifications. i.e:
   *
   * "name" = "<-describes.name"
   *
   * @param filterMap
   * @param orderMap
   * @param defaultSort
   */
  def toRestParams(filterMap: ListMap[String,String], orderMap: ListMap[String,String], defaultSort: Option[String] = None): rest.RestPageParams = {
    val combinedFilters: List[String] = for {
      name <- pfn if !name.trim.isEmpty
      value <- pfv if !value.trim.isEmpty
      propertyPath <- filterMap.get(name)
    } yield s"${propertyPath}__ICONTAINS:${value}"

    val combinedOrders: List[String] = sort.orElse(defaultSort).flatMap { orderBy =>
    // Convert a sort name with a leading minus sign to a name/DESC pair
      orderMap.get(orderBy).flatMap { propertyPath =>
        Some(s"${propertyPath}${if(reverse.getOrElse(false)) "__DESC" else ""}")
      }
    }.toList
    rest.RestPageParams(page, limit, combinedFilters, combinedOrders)
  }
}