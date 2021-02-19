package utils

import models.EntityType
import play.api.mvc.QueryStringBindable
import utils.binders.NamespaceExtractor


/**
  * Sparse field filters, used by the search API
  */
case class FieldFilter(
  et: EntityType.Value,
  fields: Seq[String] = Seq.empty
) {
  def toSeq(ns: String = ""): Seq[(String, String)] =
    Seq(s"$ns${FieldFilter.FIELDS}[$et]" -> fields.mkString(","))
}

object FieldFilter {
  val FIELDS = "fields"

  implicit val _queryBinder: QueryStringBindable[Seq[FieldFilter]] with NamespaceExtractor =
    new QueryStringBindable[Seq[FieldFilter]] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Seq[FieldFilter]]] = {
        def parse(key: String, value: Seq[String]): Option[Either[String, FieldFilter]] = {
          if (key.startsWith(s"$FIELDS[") && key.endsWith("]")) {
            val et = key.substring(7, key.length - 1)
            val fields = value.flatMap(_.split(",")).filter(_.trim.nonEmpty)
            try Some(Right(FieldFilter(EntityType.withName(et), fields))) catch {
              case e: Throwable => Some(Left(e.getMessage))
            }
          } else None
        }

        val filters = params.foldLeft(Seq.empty[FieldFilter]) { case (s, (k, v)) =>
          parse(k, v) match {
            case Some(Right(f)) => f +: s
            case _ => s
          }
        }
        Some(Right(filters))
      }

      override def unbind(key: String, value: Seq[FieldFilter]): String =
        utils.http.joinQueryString(value.flatMap(_.toSeq(ns(key))))
    }
}



