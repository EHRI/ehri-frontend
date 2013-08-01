package solr.query

case class SolrQuery(
  query: Option[String] = None,
  fields: List[String] = Nil,
  start: Option[Int] = None,
  rows: Option[Int] = None,
  debug: Boolean = false,
  sort: Option[String] = None,
  reverse:  Boolean = false


) {
  override def toString = {
    "TODO"
  }
}