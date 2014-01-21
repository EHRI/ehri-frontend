package utils.search

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait ResponseParser {
  def apply(response: String): QueryResponse
}
