package solr

import play.api.libs.json.Reads
import defines.EntityType

/**
 * User: michaelb
 */
case class SearchDescription(
  id: String,
  name: String,
  `type`: EntityType.Value,
  itemId: String,
  data: Map[String,String] = Map()
)