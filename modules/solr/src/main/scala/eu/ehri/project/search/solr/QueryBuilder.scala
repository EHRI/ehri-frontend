package eu.ehri.project.search.solr

import utils.search.SearchQuery

trait QueryBuilder {
  /**
    * Build a simple filter query.
    */
  def simpleFilterQuery(query: SearchQuery): Map[String, Seq[String]]

  /**
    * Build a full search query.
    */
  def searchQuery(query: SearchQuery): Map[String, Seq[String]]
}
