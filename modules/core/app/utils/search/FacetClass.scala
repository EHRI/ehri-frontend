package utils.search

import play.api.libs.json.{JsNumber, Json, Writes}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait FacetClass[+T <: Facet] {
  def key: String
  def name: String
  def param: String
  def facets: Seq[T]

  def sort: FacetSort.Value = FacetSort.Count
  def display: FacetDisplay.Value = FacetDisplay.List
  def count: Int = facets.length
  def sortedByName = facets.sortBy(_.name)
  def sortedByCount = facets.sortBy(_.count)
  def sorted: Seq[T] = sort match {
    case FacetSort.Name => sortedByName
    case FacetSort.Count => sortedByCount
    case _ => facets
  }
  def isActive = facets.exists(_.count > 0)
  def render: String => String = identity
  def pretty[U <: Facet](f: U): String = f.name.map(render).getOrElse(render(f.value))
  def fullKey = key

  /**
   * Facets that do not trigger filtering on item counts in the same class.
   */
  def multiSelect: Boolean = display == FacetDisplay.Choice || display == FacetDisplay.DropDown
}

object FacetClass {
  implicit def facetClassWrites: Writes[FacetClass[Facet]] = new Writes[FacetClass[Facet]] {
    def writes(fc: FacetClass[Facet]) = Json.obj(
      "count" -> JsNumber(fc.count),
      "param" -> Json.toJson(fc.param),
      "name" -> Json.toJson(fc.name),
      "key" -> Json.toJson(fc.key),
      "facets" -> Json.toJson(fc.sorted)
    )
  }
}