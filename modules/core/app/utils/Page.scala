package utils

import models.json.ClientWriteable
import play.api.libs.json.{Writes, Reads}
import backend.rest.Constants

/**
 * Class representing a page of data.
 */
case class Page[+T](
  total: Long = 0,
  page: Int = 1,
  count: Int = Constants.DEFAULT_LIST_LIMIT,
  items: Seq[T] = Seq.empty[T]
) extends utils.AbstractPage[T]

object Page {

  def empty[T] = new Page[T]

  implicit def clientFormat[T](implicit cfmt: ClientWriteable[T]): Writes[Page[T]] = {
    Page.pageWrites(cfmt.clientFormat)
  }

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit def pageWrites[T](implicit r: Writes[T]): Writes[Page[T]] = (
    (__ \ "total").write[Long] and
    (__ \ "page").write[Int] and
    (__ \ "count").write[Int] and
    (__ \ "values").lazyWrite(Writes.seq[T](r))
  )(unlift(Page.unapply[T]))
}

