import play.api.libs.json.JsResult
package object defines {

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
          case _ => JsError("Enumeration expected")
        }
      }
      case _ => JsError("String value expected")
    }
  }
}