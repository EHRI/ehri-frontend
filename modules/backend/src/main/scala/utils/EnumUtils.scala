package utils

import play.api.data.format.Formatter
import play.api.data.{FormError, Forms, Mapping}
import play.api.libs.json._

object EnumUtils {

  /**
    * Deserializer for Enumeration types.
    *
    * {{{
    * (Json \ "status").as(enum(Status))
    * }}}
    */
  def enumReads[E <: Enumeration](e: E): Reads[E#Value] = Reads[E#Value] {
    case JsString(s) =>
      try JsSuccess(e.withName(s)) catch {
        case _: NoSuchElementException =>
          JsError(s"Enumeration expected of type: '${e.getClass}', but it does not appear to contain the value: '$s'")
      }
    case _ => JsError("String value expected")
  }

  def enumWrites[E <: Enumeration]: Writes[E#Value] = Writes[E#Value](v => JsString(v.toString))

  def enumFormat[E <: Enumeration](`enum`: E): Format[E#Value] = Format(enumReads(enum), enumWrites)

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
  def enumMapping[E <: Enumeration](`enum`: E): Mapping[E#Value] = Forms.of(enumFormBinder(enum))

  /**
    * Default formatter for `scala.Enumeration`
    *
    */
  private def enumFormBinder[E <: Enumeration](e: E): Formatter[E#Value] = new Formatter[E#Value] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], E#Value] = {
      play.api.data.format.Formats.stringFormat.bind(key, data).flatMap { s =>
        scala.util.control.Exception.allCatch[E#Value]
          .either(e.withName(s))
          .left.map(e => Seq(FormError(key, "errors.invalidValue", Nil)))
      }
    }

    def unbind(key: String, value: E#Value) = Map(key -> value.toString)
  }

  /**
    * Tolerant form binder for a sequence of enumeration values.
    *
    * Takes only the valid values and ignores the rest.
    */
  def tolerantSeq[E <: Enumeration](e: E): Mapping[Seq[E#Value]] = Forms.seq(Forms.text).transform(
    strings => e.values.filter(v => strings.contains(v.toString)).toSeq,
    values => values.map(_.toString)
  )
}
