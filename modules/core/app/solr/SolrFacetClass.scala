package solr

import com.github.seratch.scalikesolr.request.query.facet.{Value, Param, FacetParam}
import utils.search.{FacetSort, FacetDisplay, FacetClass}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
sealed trait SolrFacetClass[+T <: SolrFacet] extends FacetClass[T] {
  def asParams: Seq[FacetParam]

  override def fullKey = if (multiSelect) s"{!ex=$key}$key" else key
}


/**
 * A facet class where facet values are literals, one or more of
 * which can be applied at a time.
 *
 * @param key     the name of the Solr field being faceted on
 * @param name    the 'pretty' human name of the Solr field
 * @param param   the name of the HTTP param used to apply this facet
 * @param render  a function (String => String) used to transform the
 *                facet values into human-readable ones, using, for
 *                example, i18n lookups.
 * @param facets  a list of individual Facet values
 * @param display The method used to display this facet
 * @param sort    how this facet should be sorted
 */
case class FieldFacetClass(
  key: String,
  name: String,
  param: String,
  override val render: (String) => String = s=>s,
  facets: Seq[SolrFieldFacet] = Nil,
  override val display: FacetDisplay.Value = FacetDisplay.List,
  override val sort: FacetSort.Value = FacetSort.Count
) extends SolrFacetClass[SolrFieldFacet] {
  val fieldType: String = "facet.field"

  def asParams: Seq[FacetParam] = {
    List(new FacetParam(
      Param(fieldType),
      Value(fullKey)
    ))
  }
}

/**
 * A facet class where facet values are ranges over
 * some value.
 *
 * @param key     the name of the Solr field being faceted on
 * @param name    the 'pretty' human name of the Solr field
 * @param param   the name of the HTTP param used to apply this facet
 * @param render  a function (String => String) used to transform the
 *                facet values into human-readable ones, using, for
 *                example, i18n lookups.
 * @param facets  a list of individual query facets, in the form
 *                of value ranges.
 * @param display The method used to display this facet
 * @param sort    how this facet should be sorted
 */
case class QueryFacetClass(
  key: String,
  name: String,
  param: String,
  override val render: (String) => String = s=>s,
  override val facets: Seq[SolrQueryFacet],
  override val display: FacetDisplay.Value = FacetDisplay.List,
  override val sort: FacetSort.Value = FacetSort.Name
) extends SolrFacetClass[SolrQueryFacet] {
  val fieldType: String = "facet.query"
  override def isActive = true

  def asParams: Seq[FacetParam] = {
    facets.map(p =>
      new FacetParam(
        Param(fieldType),
        Value(s"$fullKey:${p.solrValue}")
      )
    )
  }
}
