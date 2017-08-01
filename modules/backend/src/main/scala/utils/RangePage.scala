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