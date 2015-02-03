package utils.search

import language.postfixOps

/**
 * Page of search result items
 */
case class ItemPage[+A](
  items: Seq[A] = Seq.empty,
  offset: Int = 0,
  limit:Int = 0,
  total: Int = 0,
  facets: Seq[FacetClass[Facet]] = Seq.empty,
  spellcheck: Option[(String,String)] = None
) extends utils.AbstractPage[A]


object ItemPage {
  def empty[T]: ItemPage[T] = new ItemPage[T]()
}

