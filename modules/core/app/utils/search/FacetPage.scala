package utils.search

/**
 * A paged list of facets.
 * @param fc
 * @param items
 * @param offset
 * @param limit
 * @param total
 * @tparam A
 */
case class FacetPage[+A](
  fc: FacetClass[Facet],
  items: Seq[A],
  offset: Int,
  limit: Int,
  total: Long
) extends utils.AbstractPage[A]
