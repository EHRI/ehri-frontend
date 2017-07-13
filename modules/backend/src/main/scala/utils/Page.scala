package utils

import services.rest.Constants

/**
 * Class representing a page of data.
 */
case class Page[+T](
  offset: Int = 0,
  limit: Int = Constants.DEFAULT_LIST_LIMIT,
  total: Int = 0,
  items: Seq[T] = Seq.empty[T]
) extends utils.AbstractPage[T]

object Page {

  def empty[T] = new Page[T]

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit def pageWrites[T](implicit r: Writes[T]): Writes[Page[T]] = (
    (__ \ "offset").write[Int] and
    (__ \ "limit").write[Int] and
    (__ \ "total").write[Int] and
    (__ \ "values").lazyWrite(Writes.seq[T](r))
  )(unlift(Page.unapply[T]))
}

