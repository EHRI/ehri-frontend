package eu.ehri.project.search.solr

trait QueryBuilder {
  /**
    * Build a simple filter query.
    */
  def simpleFilterQuery(alphabetical: Boolean = false): Map[String, Seq[String]]

  /**
    * Build a full search query.
    */
  def searchQuery(): Map[String, Seq[String]]
}
