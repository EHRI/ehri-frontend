package defines

import play.api.mvc.{PathBindable, QueryStringBindable}

/**
 * Enum that can be used in path parameters and query strings to
 * provide a value constraint.
 */
abstract class BindableEnum extends Enumeration {

  // Allow these enums to be converted to strings implicitly,
  // since the binding relies on to/from string behaviour.
  import language.implicitConversions
  implicit def enumToString(e: Enumeration#Value): String = e.toString

  implicit def bindableEnum: PathBindable[Value] =
    binders.bindableEnum(this)

  implicit def queryStringBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Value] =
    binders.queryStringBinder(this)
}

