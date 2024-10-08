package forms

import models.Entity
import play.api.data.Forms.{optional, text}

package object mappings {

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
  val entityForm: Mapping[Entity] = Forms.of(new Formatter[Entity] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Entity] = {
      play.api.data.format.Formats.stringFormat.bind(key, data).flatMap { s =>
        scala.util.control.Exception.allCatch[Entity]
          .either(Json.parse(s).as[Entity](Entity.entityReads))
          .left.map(e => Seq(FormError(key, "error.jsonObj", Nil)))
      }
    }

    def unbind(key: String, value: Entity): Map[String, String] = Map(key -> Json.stringify(Json.toJson(value)(Entity.entityWrites)))
  })


  /**
    * A mapping for optional text values that become `None` if they are empty or contain only whitespace.
    */
  val optionalText: Mapping[Option[String]] = optional(text).transform(_.filter(_.trim.nonEmpty), identity)

}
