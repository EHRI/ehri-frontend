package utils

import play.api.mvc.QueryStringBindable
import services.data.Constants._
import utils.binders.NamespaceExtractor

/**
  * Class for handling page parameter data
  */
case class PageParams(page: Int = 1, limit: Int = DEFAULT_LIST_LIMIT) extends Ranged {
  def withoutLimit: PageParams = copy(limit = -1)

  def offset: Int = (page - 1) * limit.max(0)

  def next: PageParams = copy(page + 1)

  def prev: PageParams = copy(1.max(page - 1))
}

object PageParams {
  def empty: PageParams = PageParams()

  implicit val _queryBinder: QueryStringBindable[PageParams] =
    new QueryStringBindable[PageParams] with NamespaceExtractor {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PageParams]] = {
        val namespace: String = ns(key)
        Some(Right(PageParams(
          bindOr(namespace + PAGE_PARAM, params, 1).max(1),
          bindOr(namespace + LIMIT_PARAM, params, DEFAULT_LIST_LIMIT).min(MAX_LIST_LIMIT)
        )))
      }

      override def unbind(key: String, params: PageParams): String =
        utils.http.joinQueryString(toParams(params, ns(key)).distinct)


      private def toParams(p: PageParams, ns: String = ""): Seq[(String, String)] = {
        val pg = if (p.page == 1) Seq.empty else Seq(p.page.toString)
        val lm = if (p.limit == DEFAULT_LIST_LIMIT) Seq.empty else Seq(p.limit.toString)
        pg.map(ns + PAGE_PARAM -> _) ++ lm.map(ns + LIMIT_PARAM -> _)
      }
    }
}

