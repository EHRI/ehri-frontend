package eu.ehri.project.search.solr

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ResponseHandler {
  def getResponseParser(responseBody: String): QueryResponseExtractor
}
