package models

import play.api.libs.json.{Writes, JsValue, Reads, Json, JsObject}
import play.api.data.{FormError, Forms, Mapping}
import play.api.data.format.Formatter

package object forms {

  import play.api.data.format.Formatter
  import play.api.data.{ FormError, Forms, Mapping }

  /**
   * Constructs a simple mapping for a text field (mapped as `scala.Enumeration`)
   *
   * For example:
   * {{{
   *   Form("status" -> enum(Status))
   * }}}
   *
   * @param enum the Enumeration#Value
   */
  def enum[E <: Enumeration](enum: E): Mapping[E#Value] = Forms.of(enumFormat(enum))

  /**
   * Default formatter for `scala.Enumeration`
   *
   */
  def enumFormat[E <: Enumeration](enum: E): Formatter[E#Value] = new Formatter[E#Value] {
    def bind(key: String, data: Map[String, String]) = {
      play.api.data.format.Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[E#Value]
          .either(enum.withName(s))
          .left.map(e => Seq(FormError(key, "error.enum", Nil)))
      }
    }
    def unbind(key: String, value: E#Value) = Map(key -> value.toString)
  }

  /**
   * Constructs a simple mapping for a text field (mapped as `JsObject`)
   *
   * For example:
   * {{{
   *   Form("randomData" -> jsonObj(Status))
   * }}}
   */
  def entity: Mapping[Entity] = Forms.of(entityMapping)

  /**
   * Default formatter for `scala.Enumeration`
   *
   */
  def entityMapping: Formatter[Entity] = new Formatter[Entity] {
    def bind(key: String, data: Map[String, String]) = {
      play.api.data.format.Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[Entity]
          .either(Json.parse(s).as[Entity](models.json.entityReads))
          .left.map(e => Seq(FormError(key, "error.jsonObj", Nil)))
      }
    }
    def unbind(key: String, value: Entity) = Map(key -> Json.stringify(Json.toJson(value)(models.json.entityWrites)))
  }

}