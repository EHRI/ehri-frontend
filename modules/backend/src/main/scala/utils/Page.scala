package utils

import services.data.Constants

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
}

