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

  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}
