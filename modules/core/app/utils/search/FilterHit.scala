package utils.search

import defines.EntityType
import scala.annotation.tailrec
import scala._
import solr.SolrConstants._
import play.api.templates.{HtmlFormat, Html}
import play.api.libs.json.Json

/**
 * Class representing a search engine filter hit
 */
case class FilterHit(
  id: String,
  did: String,
  name: String,
  `type`: EntityType.Value,
  parent: Option[String] = None,
  gid: Long
)

object FilterHit {
  implicit val fmt = Json.format[FilterHit]
}
