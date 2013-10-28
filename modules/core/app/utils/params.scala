package utils

import play.api.mvc.RequestHeader
import play.api.data.Form
import play.api.data.Forms._
import rest.Constants._

/**
 * A list offset and limit.
 */
case class ListParams(offset: Int = 0, limit: Int = DEFAULT_LIST_LIMIT) {
  def toSeq: Seq[(String,String)]
      = (List(OFFSET_PARAM -> offset.toString) ::: List(LIMIT_PARAM -> limit.toString)).toSeq
}

object ListParams {
  def fromRequest(request: RequestHeader, namespace: String = ""): ListParams = {
    Form(
      mapping(
        namespace + OFFSET_PARAM -> default(number, 0),
        namespace + LIMIT_PARAM -> default(number, DEFAULT_LIST_LIMIT)
      )(ListParams.apply)(ListParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(new ListParams())
  }
}

object PageParams {

  def fromRequest(request: RequestHeader, namespace: String = ""): PageParams = {
    Form(
      mapping(
        namespace + PAGE_PARAM -> default(number, 1),
        namespace + LIMIT_PARAM -> default(number, DEFAULT_LIST_LIMIT)
      )(PageParams.apply)(PageParams.unapply)
    ).bindFromRequest(request.queryString).value.getOrElse(new PageParams())
  }
}

/**
 * Class for handling page parameter data
 */
case class PageParams(page: Int = 1, limit: Int = DEFAULT_LIST_LIMIT) {
  def offset: Int = (page - 1) * limit
  def range: String = s"$offset-${offset + limit}"

  def toSeq: Seq[(String,String)]
        = (List(OFFSET_PARAM -> offset.toString) ::: List(LIMIT_PARAM -> limit.toString)).toSeq
}
