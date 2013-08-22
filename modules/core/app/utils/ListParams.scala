package utils

import play.api.mvc.{AnyContent, Request}
import play.api.data.Form

object ListParams {
  final val OFFSET_PARAM = "offset"
  final val LIMIT_PARAM = "limit"
  final val PAGE_PARAM = "page"

  final val DEFAULT_LIST_LIMIT = 20

  def fromRequest(request: Request[AnyContent], namespace: String = ""): ListParams = {
    import play.api.data.Forms._

    // NB: There *should* be no way for the binding
    // of this form to fail, since we have no
    // constraints.
    Form(
      mapping(
        namespace + PAGE_PARAM -> optional(number),
        namespace + LIMIT_PARAM -> optional(number)
      )(ListParams.apply)(ListParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(new ListParams())
  }
}

/**
 * Class for handling page parameter data
 * @param page
 * @param limit
 */
case class ListParams(page: Option[Int] = None, limit: Option[Int] = None) {

  import ListParams._

  def mergeWith(default: ListParams): ListParams = ListParams(
    page = page.orElse(default.page),
    limit = limit.orElse(default.limit)
  )

  def offset: Int = (page.getOrElse(1) - 1) * limit.getOrElse(DEFAULT_LIST_LIMIT)
  def range: String = s"$offset-${offset + limit.getOrElse(DEFAULT_LIST_LIMIT)}"

  def toSeq: Seq[(String,String)]
        = (List(OFFSET_PARAM -> offset.toString) :::
            List(LIMIT_PARAM -> limit.getOrElse(DEFAULT_LIST_LIMIT).toString)).toSeq
}
