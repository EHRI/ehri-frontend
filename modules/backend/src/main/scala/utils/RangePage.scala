package utils

/**
 * A range represents a window in a potentially infinite
 * set of data
 */
case class RangePage[+T](
  offset: Int,
  limit: Int,
  items: Seq[T],
  more: Boolean
) extends Iterable[T] {
  def length: Int = items.length
  def iterator: Iterator[T] = items.iterator
}

object RangePage {

  val OFFSET = "offset"
  val LIMIT = "limit"
  val MORE = "more"

  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  implicit def _writes[T](implicit w: Writes[T]): Writes[RangePage[T]] = (
    (__ \ OFFSET).write[Int] and
    (__ \ LIMIT).write[Int] and
    (__ \ "values").lazyWrite(Writes.seq[T](w)) and
    (__ \ MORE).write[Boolean]
  )(unlift(RangePage.unapply[T]))
}

