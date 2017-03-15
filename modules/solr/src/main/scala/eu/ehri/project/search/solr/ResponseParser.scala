package eu.ehri.project.search.solr

import utils.search.{AppliedFacet, Facet, FacetClass}

trait ResponseParser {
  def parse(responseBody: String, allFacets: Seq[FacetClass[Facet]], appliedFacets: Seq[AppliedFacet]): QueryResult
}