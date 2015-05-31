package utils.search

import defines.EntityType
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
