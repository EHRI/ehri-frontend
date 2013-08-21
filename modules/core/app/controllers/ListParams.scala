package controllers

import play.api.mvc.{AnyContent, Request}
import play.api.data.Form
import collection.immutable.ListMap


object ListParams {
  final val PAGE = "page"
  final val LIMIT = "limit"

  def bind(request: Request[AnyContent]): ListParams = {
    import play.api.data.Forms._
    import rest.RestPageParams._

    // NB: There *should* be no way for the binding
    // of this form to fail, since we have no
    // constraits
    Form(
      mapping(
        PAGE -> optional(number),
        LIMIT -> optional(number)
      )(ListParams.apply)(ListParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(new ListParams())
  }
}

/**
 * Class representing the query parameters for a list view.
 * @param page    page number
 * @param limit   per-page limit
 */
case class ListParams(
  page: Option[Int] = None,
  limit: Option[Int] = None
) {

  /**
   * Converts a list param set into a REST param set, given the necessary
   * mappings between our filter and sort values, and the server's filter
   * and sort specifications. i.e:
   */
  def toRestParams: rest.RestPageParams = {
    rest.RestPageParams(page, limit)
  }
}