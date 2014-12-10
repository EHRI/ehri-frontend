package utils.search

import play.api.libs.json._
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNumber

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */


case object FacetSort extends Enumeration {
  val Name = Value("name")
  val Count = Value("count")
  val Fixed = Value("fixed")
}

case object FacetDisplay extends Enumeration {
  val Choice = Value("choice")
  val List = Value("list")
  val DropDown = Value("dropdown")
  val Boolean = Value("boolean")
  val Date = Value("date")
}


trait Facet {
  val value: String
  val name: Option[String]
  val applied: Boolean
  val count: Int
  def sortName: String = name.getOrElse(value)
}

object Facet {
  implicit def facetWrites: Writes[Facet] = new Writes[Facet] {
    def writes(f: Facet) = Json.obj(
      "count" -> JsNumber(f.count),
      "value" -> JsString(f.value),
      "name" -> Json.toJson(f.name),
      "applied" -> JsBoolean(f.applied)
    )
  }
}

trait FacetClass[+T <: Facet] {
  val key: String
  val name: String
  val param: String
  val facets: List[T]
  val sort: FacetSort.Value
  val display: FacetDisplay.Value
  val fieldType: String

  def count: Int = facets.length
  def sortedByName = facets.sortWith((a, b) => a.sortName < b.sortName)
  def sortedByCount = facets.sortWith((a, b) => b.count < a.count)
  def sorted: List[T] = sort match {
    case FacetSort.Name => sortedByName
    case FacetSort.Count => sortedByCount
    case _ => facets
  }
  def isActive = facets.exists(_.count > 0)
  def render: String => String

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

