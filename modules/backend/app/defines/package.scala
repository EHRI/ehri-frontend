import play.api.mvc.QueryStringBindable

package object defines {

  import language.implicitConversions
  implicit def enumToString(e: Enumeration#Value) = e.toString

  import play.api.libs.json._

  import play.api.mvc.PathBindable

  abstract class BindableEnum extends Enumeration {
    
    implicit def bindableEnum = new PathBindable[Value] {
      def bind(key: String, value: String) =
        values.find(_.toString.toLowerCase == value.toLowerCase) match {
          case Some(v) => Right(v)
          case None => Left("Unknown url path segment '" + value + "'")
        }
      def unbind(key: String, value: Value) = value.toString.toLowerCase
    }

    implicit def queryStringBinder(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[Value] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Value]] = {
        for {
          v <- stringBinder.bind(key, params)
        } yield {
          v match {
            case Right(p) if values.exists(_.toString.toLowerCase == p.toLowerCase) =>
              Right(withName(p.toLowerCase))
            case _ => Left("Unable to bind a valid value from alternatives: " + values)
          }
        }
      }
      override def unbind(key: String, value: Value): String = {
        stringBinder.unbind(key, value)
      }
    }
  }

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
            case _: NoSuchElementException => JsError("Enumeration expected of type: '%s', but it does not appear to contain the value: '%s'".format(enum.getClass, s))
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