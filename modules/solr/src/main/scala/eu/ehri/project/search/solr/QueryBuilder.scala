package eu.ehri.project.search.solr

import utils.search.SearchQuery

trait QueryBuilder {
  /**
    * Build a simple filter query.
    */
  def simpleFilterQuery(query: SearchQuery, alphabetical: Boolean = false): Seq[(String, String)]

  /**
    * Build a full search query.
    */
  def searchQuery(query: SearchQuery): Seq[(String, String)]
}
