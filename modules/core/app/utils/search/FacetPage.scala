package utils.search

/**
 * A paged list of facets.
 * @param fc
 * @param items
 * @param page
 * @param count
 * @param total
 * @tparam A
 */
case class FacetPage[+A](
  fc: FacetClass[Facet],
  items: Seq[A],
  page: Int,
  count: Int,
  total: Long
) extends utils.AbstractPage[A]
