package utils

import java.net.{MalformedURLException, URL}
import backend.Entity

/**
 * Form-related utilities
 */
package object forms {


  /**
   * Check if a string is a valid URL.
   * @param s url string
   * @return
   */
  def isValidUrl(s: String): Boolean = {
    try {
      new URL(s)
      true
    } catch {
      case s: MalformedURLException => false
    }
  }

  import play.api.data.format.Formatter
  import play.api.data.{FormError, Forms, Mapping}
  import play.api.libs.json.Json

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
          .either(Json.parse(s).as[Entity](Entity.entityReads))
          .left.map(e => Seq(FormError(key, "error.jsonObj", Nil)))
      }
    }
    def unbind(key: String, value: Entity) = Map(key -> Json.stringify(Json.toJson(value)(Entity.entityWrites)))
  }
}