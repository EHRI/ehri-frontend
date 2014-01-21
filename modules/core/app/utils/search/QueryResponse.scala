package utils.search

/**
 * Trait that exposes all the relevant information
 * we need to extract from a search query response.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait QueryResponse {
  def items: Seq[SearchHit]
  def extractFacetData(appliedFacets: List[AppliedFacet], allFacets: FacetClassList): FacetClassList
  def count: Int
  def highlightMap: Map[String,Map[String,Seq[String]]]
  def spellcheckSuggestion: Option[(String,String)]
}
