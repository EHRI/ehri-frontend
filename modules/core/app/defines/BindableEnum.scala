package defines

import play.api.mvc.{PathBindable, QueryStringBindable}

import scala.language.implicitConversions

/**
 * Enum that can be used in path parameters and query strings to
 * provide a value constraint.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
abstract class BindableEnum extends Enumeration {

  // Allow these enums to be converted to strings implicitly,
  // since the binding relies on to/from string behaviour.
  import language.implicitConversions
  implicit def enumToString(e: Enumeration#Value): String = e.toString

  implicit def bindableEnum: PathBindable[Value] = new PathBindable[Value] {
    def bind(key: String, value: String) =
      values.find(_.toString.toLowerCase == value.toLowerCase) match {
        case Some(v) => Right(v)
        case None => Left("Unknown url path segment '" + value + "'")
      }
    def unbind(key: String, value: Value) = value.toString.toLowerCase
  }

  implicit def queryStringBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Value] = new QueryStringBindable[Value] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Value]] =
      for {
        v <- stringBinder.bind(key, params)
      } yield {
        v match {
          case Right(p) if values.exists(_.toString.toLowerCase == p.toLowerCase) =>
            Right(withName(p.toLowerCase))
          case _ => Left("Unable to bind a valid value from alternatives: " + values)
        }
      }

    override def unbind(key: String, value: Value): String = stringBinder.unbind(key, value)
  }
}

