import play.api.libs.json.JsResult

package object defines {

  implicit def enumToString(e: Enumeration#Value) = e.toString

  import play.api.libs.json._

  object EnumUtils {

    /**
     * Deserializer for Enumeration types.
     *
     * {{{
     * (Json \ "status").as(enum(Status))
     * }}}
     */
    def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
      def reads(json: JsValue): JsResult[E#Value] = json match {
        case JsString(s) => {
          try {
            JsSuccess(enum.withName(s))
          } catch {
            case _: NoSuchElementException => JsError("Enumeration expected of type: '%s', but it does not appear formable contain the value: '%s'".format(enum.getClass, s))
          }
        }
        case _ => JsError("String value expected")
      }
    }

    implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
      def writes(v: E#Value): JsValue = JsString(v.toString)
    }

    implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = Format(enumReads(enum), enumWrites)
  }
}