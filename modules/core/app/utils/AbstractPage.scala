package utils

/**
 * Abstract result page.
 * @tparam A
 */
trait AbstractPage[+A] extends Seq[A] {
  def iterator = items.iterator
  val total: Long
  val page: Int
  val count: Int
  val items: Seq[A]
  def length = items.length
  def numPages = (total / count) + (total % count).min(1)
  def hasMultiplePages = total > count
  def apply(i: Int): A = items.apply(i)
  def start = (page - 1) * count
  def end = start + items.size
  def range: String = s"$start-$end"

  def isLimited = count == -1
}
