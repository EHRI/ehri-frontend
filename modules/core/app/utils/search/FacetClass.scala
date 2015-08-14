package utils.search

import play.api.libs.json.{JsNumber, Json, Writes}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
sealed trait FacetClass[+T <: Facet] {
  def key: String
  def name: String
  def param: String
  def facets: Seq[T]

  def sort: FacetSort.Value = FacetSort.Count
  def display: FacetDisplay.Value = FacetDisplay.List
  def count: Int = facets.length
  def limit: Option[Int] = None
  def minCount: Option[Int] = None

  /**
   * This key is a valid facet value. By default any values
   * are accepted, except for query facets.
   */
  def isValidValue(s: String) = true

  /**
   * A facet class is deemed active if either it has applied facets
   * or it contains facets which are worth applying, e.g. have a
   * non-zero item count.
   */
  def isActive = facets.exists(_.applied) || facets.exists(_.count > 0)
  def render: String => String = identity
  def pretty[U <: Facet](f: U): String = f.name.map(render).getOrElse(render(f.value))

  /**
   * Facets that do not trigger filtering on item counts in the same class.
   */
  def multiSelect: Boolean = display == FacetDisplay.Choice || display == FacetDisplay.DropDown
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
  override val limit: Option[Int] = None,
  override val minCount: Option[Int] = None,
  override val render: (String) => String = s=>s,
  facets: Seq[FieldFacet] = Nil,
  override val display: FacetDisplay.Value = FacetDisplay.List,
  override val sort: FacetSort.Value = FacetSort.Count
) extends FacetClass[FieldFacet]

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
  override val facets: Seq[QueryFacet],
  override val display: FacetDisplay.Value = FacetDisplay.List,
  override val sort: FacetSort.Value = FacetSort.Name
) extends FacetClass[QueryFacet] {
  override def isValidValue(s: String) = facets.exists(_.value == s)
}


object FacetClass {
  implicit def facetClassWrites: Writes[FacetClass[Facet]] = new Writes[FacetClass[Facet]] {
    def writes(fc: FacetClass[Facet]) = Json.obj(
      "count" -> JsNumber(fc.count),
      "param" -> Json.toJson(fc.param),
      "name" -> Json.toJson(fc.name),
      "key" -> Json.toJson(fc.key),
      "facets" -> Json.toJson(fc.facets)
    )
  }
}