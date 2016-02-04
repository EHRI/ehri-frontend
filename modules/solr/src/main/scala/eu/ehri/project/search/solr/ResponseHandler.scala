package eu.ehri.project.search.solr

trait ResponseHandler {
  def getResponseParser(responseBody: String): QueryResponseExtractor
}
