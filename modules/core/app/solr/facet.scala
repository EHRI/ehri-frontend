package solr.facet

import com.github.seratch.scalikesolr.request.query.facet.{FacetParam, Param, Value}
import play.api.libs.json.{JsNumber, Json, Writes}
import utils.search.{FacetDisplay, FacetClass, FacetSort}

trait SolrFacet extends utils.search.Facet {
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
  def sort = name.getOrElse(value)
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
) extends SolrFacet {
  def sort = name.getOrElse(value)
}


trait SolrFacetClass[+T <: SolrFacet] extends FacetClass[T] {
  def asParams: List[FacetParam]

  override def fullKey = if (multiSelect) s"{!ex=$key}$key" else key
}


/**
 *
 * @param key     the name of the Solr field being faceted on
 * @param name    the 'pretty' human name of the Solr field
 * @param param   the name of the HTTP param used to apply this facet
 * @param render  a function (String => String) used to transform the
 *                facet values into human-readable ones, using, for
 *                example, i18n lookups.
 * @param facets  a list of individual Facet values
 * @param sort
 */
case class FieldFacetClass(
  key: String,
  name: String,
  param: String,
  render: (String) => String = s=>s,
  facets: List[SolrFieldFacet] = Nil,
  display: FacetDisplay.Value = FacetDisplay.List,
  sort: FacetSort.Value = FacetSort.Count
) extends SolrFacetClass[SolrFieldFacet] {
  val fieldType: String = "facet.field"

  def asParams: List[FacetParam] = {
    List(new FacetParam(
      Param(fieldType),
      Value(fullKey)
    ))
  }
}

case class QueryFacetClass(
  key: String,
  name: String,
  param: String,
  render: (String) => String = s=>s,
  override val facets: List[SolrQueryFacet],
  display: FacetDisplay.Value = FacetDisplay.List,
  sort: FacetSort.Value = FacetSort.Name
) extends SolrFacetClass[SolrQueryFacet] {
  val fieldType: String = "facet.query"

  def asParams: List[FacetParam] = {
    facets.map(p =>
      new FacetParam(
        Param(fieldType),
        Value(s"$fullKey:${p.solrValue}")
      )
    )
  }
}


