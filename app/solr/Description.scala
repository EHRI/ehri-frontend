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
  `itemId`: String
)

object SearchDescription {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val descriptionReads: Reads[SearchDescription] = (
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "type").read[EntityType.Value](defines.EnumReader.enumReads(EntityType)) and
    (__ \ "entityId").read[String]
  )(SearchDescription.apply _)
}
