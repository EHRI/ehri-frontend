package utils

/**
 * Abstract result page.
 * @tparam A
 */
trait AbstractPage[+A] extends Seq[A] {
  def total: Int
  def offset: Int
  def limit: Int
  def items: Seq[A]

  def iterator = items.iterator
  def length = items.length
  def numPages: Int = (total / limit.max(1)) + (total % limit.max(1)).min(1)
  def hasMultiplePages = total > items.size
  def apply(i: Int): A = items.apply(i)
  def start: Int = offset + 1
  def end: Int = offset + items.size
  def page: Int = (Math.ceil(offset.max(0).toDouble / limit.max(1).toDouble) + 1).toInt
  def range: String = s"$start-$end"
  def hasMore: Boolean = page < numPages

  def isLimited = limit == -1
}
