package utils

/**
 * Abstract result page.
 * @tparam A
 */
trait AbstractPage[+A] {
  val total: Long
  val offset: Int
  val limit: Int
  val items: Seq[A]
  def page = (offset / limit) + 1
  def numPages = (total / limit) + (total % limit).min(1)
  def hasMultiplePages = total > limit
  def start = offset + 1
  def end = (offset + 1) + items.size
  def range: String = s"$start-$end"

  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)

  def isEmpty = total == 0 // items.isEmpty? Not sure of the best semantics here?
}
