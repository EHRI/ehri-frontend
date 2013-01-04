import play.api.libs.json.JsResult
package object defines {

  implicit def enumToString(e: Enumeration#Value) = e.toString

  import play.api.libs.json.{ Writes, JsString, JsValue, Reads, JsSuccess, JsError }

  /**
   * Deserializer for Enumeration types.
   *
   * {{{
   * (Json \ "status").as(enum(Status))
   * }}}
   */
  def enum[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _ => JsError("Enumeration expected of type: '%s', but it does not appear to contain the value: '%s'".format(enum.getClass, s))
        }
      }
      case _ => JsError("String value expected")
    }
  }

  object EnumReader {
    def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
      def reads(json: JsValue): JsResult[E#Value] = json match {
        case JsString(s) => {
          try {
            JsSuccess(enum.withName(s))
          } catch {
            case _ => JsError("Enumeration expected of type: '%s', but it does not appear to contain the value: '%s'".format(enum.getClass, s))
          }
        }
        case _ => JsError("String value expected")
      }
    }
  }

  object EnumWriter {
    implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
      def writes(v: E#Value): JsValue = JsString(v.toString)
    }
  }
}