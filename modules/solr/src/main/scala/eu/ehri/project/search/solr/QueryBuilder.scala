package eu.ehri.project.search.solr

trait QueryBuilder {
  /**
    * Build a simple filter query.
    */
  def simpleFilterQuery(alphabetical: Boolean = false): Seq[(String, String)]

  /**
    * Build a full search query.
    */
  def searchQuery(): Seq[(String, String)]
}
