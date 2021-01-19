package utils

import play.api.mvc.QueryStringBindable
import services.data.Constants.{DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT}
import utils.binders.NamespaceExtractor
import services.data.Constants._

case class RangeParams(offset: Int = 0, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit: RangeParams = copy(limit = -1)

  def next: RangeParams = if (hasLimit) copy(offset + limit, limit) else this

  def prev: RangeParams = if (hasLimit) copy(0.max(offset - limit), limit) else this
}


object RangeParams {
  def empty: RangeParams = RangeParams()

  implicit val _queryBinder: QueryStringBindable[RangeParams] =
    new QueryStringBindable[RangeParams] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, RangeParams]] = {
        val namespace: String = ns(key)
        Some(Right(RangeParams(
          bindOr(namespace + OFFSET_PARAM, params, 0).max(0),
          bindOr(namespace + LIMIT_PARAM, params, DEFAULT_LIST_LIMIT).min(MAX_LIST_LIMIT)
        )))
      }

      override def unbind(key: String, params: RangeParams): String =
        utils.http.joinQueryString(toParams(params, ns(key)).distinct)

      private def toParams(p: RangeParams, ns: String = ""): Seq[(String, String)] = {
        val os = if (p.offset == 0) Seq.empty else Seq(p.offset.toString)
        val lm = if (p.limit == DEFAULT_LIST_LIMIT) Seq.empty else Seq(p.limit.toString)
        os.map(ns + OFFSET_PARAM -> _) ++ lm.map(ns + LIMIT_PARAM -> _)
      }
    }
}



