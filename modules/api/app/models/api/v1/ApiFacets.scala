package models.api.v1

import play.api.mvc.QueryStringBindable

object ApiFacet extends Enumeration {
  val Type = Value("type")
  val Lang = Value("lang")
  val Country = Value("country")
  val Holder = Value("holder")
  val Date = Value("dates")

  def fromString(s: String): Option[ApiFacet.Value] = values.find(_.toString == s)
}

case class ApiFacets(values: Map[String, Seq[String]])

object ApiFacets {
  def empty: ApiFacets = ApiFacets(Map.empty)

  implicit def _queryBinder: QueryStringBindable[ApiFacets]  = new QueryStringBindable[ApiFacets] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApiFacets]] = {
      val keys = ApiFacet.values.map(_.toString)
      val values = params.filter { case (key, _) => keys.contains(key) }
      Some(Right(ApiFacets(values)))
    }

    override def unbind(key: String, value: ApiFacets): String = utils.http.joinQueryString(value.values)
  }
}
