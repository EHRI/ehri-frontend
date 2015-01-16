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


sealed trait Facet {
  def value: String
  def name: Option[String]
  def applied: Boolean
  def count: Int
}

trait FieldFacet extends Facet
trait QueryFacet extends Facet

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

