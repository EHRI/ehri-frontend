package eu.ehri.project.search.solr

import utils.search.{AppliedFacet, Facet, FacetClass, SearchHit}

/**
  * Trait that exposes all the relevant information
  * we need to extract from a search query response.
  */
trait QueryResponseExtractor {
  def phrases: Seq[String]

  def items: Seq[SearchHit]

  def extractFacetData(appliedFacets: Seq[AppliedFacet], allFacets: Seq[FacetClass[Facet]]): Seq[FacetClass[Facet]]

  def count: Int

  def highlightMap: Map[String, Map[String, Seq[String]]]

  def spellcheckSuggestion: Option[(String, String)]

  def facetInfo: Map[String, Any]
}
