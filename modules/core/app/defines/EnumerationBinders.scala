package defines

import play.api.mvc.{QueryStringBindable, PathBindable}

import scala.language.implicitConversions

object EnumerationBinders {
  implicit def bindableEnum[E <: Enumeration](enum: E): PathBindable[E#Value] = new PathBindable[E#Value] {
    def bind(key: String, value: String) =
      enum.values.find(_.toString.toLowerCase == value.toLowerCase) match {
        case Some(v) => Right(v)
        case None => Left("Unknown url path segment '" + value + "'")
      }
    def unbind(key: String, value: E#Value) = value.toString.toLowerCase
  }

  implicit def queryStringBinder[E <: Enumeration](enum: E)(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[E#Value] = new QueryStringBindable[E#Value] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, E#Value]] =
      for {
        v <- stringBinder.bind(key, params)
      } yield {
        v match {
          case Right(p) if enum.values.exists(_.toString.toLowerCase == p.toLowerCase) =>
            Right(enum.withName(p.toLowerCase))
          case _ => Left("Unable to bind a valid value from alternatives: " + enum.values)
        }
      }

    override def unbind(key: String, value: E#Value): String = stringBinder.unbind(key, value.toString)
  }
}
