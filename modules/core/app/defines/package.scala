import anorm.{ToStatement, TypeDoesNotMatch, MayErr, Column}

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
  }

  trait StorableEnum {
    self: Enumeration =>

    implicit def rowToEnum: Column[Value] = {
      Column.nonNull[Value] { (value, meta) =>
        try {
          MayErr(Right(withName(value.toString)))
        } catch {
          case e: Throwable => Left(TypeDoesNotMatch(
            s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
        }
      }
    }

    implicit def enumToStatement = new ToStatement[Value] {
      def set(s: java.sql.PreparedStatement, index: Int, value: Value): Unit =
        s.setObject(index, value.toString)
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