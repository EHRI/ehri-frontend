package utils.binders

import play.api.mvc.QueryStringBindable

trait NamespaceExtractor {
  protected def ns(key: String): String =
    if (key.contains("_")) key.substring(key.lastIndexOf("_") + 1) else ""

  protected def bindOr[T](key: String, params: Map[String, Seq[String]], or: T)(implicit b: QueryStringBindable[T]): T =
    b.bind(key, params).map(_.fold(err => or, v => v)).getOrElse(or)
}
