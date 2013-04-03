package solr

import solr.facet.{FacetClass, AppliedFacet}
import scala.xml.{Node, Elem}
import defines.EntityType

/**
 * User: michaelb
 */
object SolrQueryParser {
  def apply(responseString: String) = new SolrQueryParser(xml.XML.loadString(responseString))
}

/**
 * Helper class for parsing a Solr XML Response.
 * @param response
 */
case class SolrQueryParser(response: Elem) {
  /**
   * Fetch the search description items returned in this response.
   */
  lazy val items: Seq[SearchDescription] = (response \ "lst" \ "lst" \ "result" \ "doc").map { doc =>
    SearchDescription(
      id = (doc \\ "str").filter(attributeValueEquals("id")).text,
      itemId = (doc \\ "str").filter(attributeValueEquals("itemId")).text,
      name = (doc \\ "str").filter(attributeValueEquals("name")).text
    )
  }

  /**
   * Count the number of search descriptions returned in this response.
   */
  lazy val count: Int = {
    val s = (response \ "lst" \ "lst" \ "int").filter(attributeValueEquals("ngroups")).text
    try {
      s.toInt
    } catch {
      case e: NumberFormatException => 0
    }
  }

  /**
   * Extract the facet data from this response, given a list of the facets
   * used to constrain the response, and the complete set of facet info requested.
   * @param appliedFacets
   * @param allFacets
   * @return
   */
  def extractFacetData(appliedFacets: List[AppliedFacet], allFacets: List[FacetClass]): List[FacetClass] = {
    allFacets.map(_.populateFromSolr(response, appliedFacets))
  }

  private def attributeValueEquals(value: String)(node: Node) = {
    node.attributes.exists(_.value.text == value)
  }
}
