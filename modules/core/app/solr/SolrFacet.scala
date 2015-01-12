package solr

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
import com.github.seratch.scalikesolr.request.query.facet.{FacetParam, Param, Value}
import play.api.libs.json.Json
import utils.search.{FacetDisplay, FacetClass, FacetSort}

sealed trait SolrFacet extends utils.search.Facet {
  def solrValue: String
}

/**
 * Encapsulates a single facet.
 *
 * @param solr   the value of this facet to Solr
 * @param value  the value as a web parameter
 * @param name  the human-readable value
 * @param count     the number of objects to which this facet applies
 * @param applied   whether or not this facet is activated in the response
 */
case class SolrFieldFacet(
  solr: String,
  value: String,
  name: Option[String] = None,
  count: Int = 0,
  applied: Boolean = false
) extends SolrFacet {
  def solrValue = solr
}

object SolrFieldFacet {
  implicit val facetWrites = Json.writes[SolrFieldFacet]
}

case class SolrQueryFacet(
  count: Int = 0,
  applied: Boolean = false,
  name: Option[String] = None,
  value: String,
  solrValue: String
) extends SolrFacet


