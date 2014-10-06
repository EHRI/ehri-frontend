package utils

/**
 * Abstract result page.
 * @tparam A
 */
trait AbstractPage[+A] extends Seq[A] {
  def total: Long
  def page: Int
  def count: Int
  def items: Seq[A]

  def iterator = items.iterator
  def length = items.length
  def numPages = (total / count.max(1)) + (total % count.max(1)).min(1)
  def hasMultiplePages = total > count
  def apply(i: Int): A = items.apply(i)
  def start = offset + 1
  def end = start + items.size - 1
  def offset = Math.max(0, (page - 1) * count)
  def range: String = s"$start-$end"
  def hasMore: Boolean = page < numPages

  def isLimited = count == -1
}
