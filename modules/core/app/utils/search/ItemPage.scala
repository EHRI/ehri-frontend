package utils.search

import language.postfixOps

/**
 * Page of search result items
 */
case class ItemPage[+A](
  items: Seq[A] = Seq.empty,
  page: Int = 1,
  count:Int = 0,
  total: Long = 0,
  facets: utils.search.FacetClassList = List.empty,
  spellcheck: Option[(String,String)] = None
) extends utils.AbstractPage[A]


object ItemPage {
  def empty[T]: ItemPage[T] = new ItemPage[T]()
}

