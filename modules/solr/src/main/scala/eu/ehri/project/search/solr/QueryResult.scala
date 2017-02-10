package eu.ehri.project.search.solr

import utils.search.{Facet, FacetClass, SearchHit}

case class QueryResult(
  phrases: Seq[String],
  items: Seq[SearchHit],
  facets: Seq[FacetClass[Facet]],
  count: Int,
  highlightMap: Map[String, Map[String, Seq[String]]],
  spellcheckSuggestion: Option[(String, String)],
  facetInfo: Map[String, Any]
)
