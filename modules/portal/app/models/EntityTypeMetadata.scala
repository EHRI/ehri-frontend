package models

import java.time.Instant

/**
  * This class contains metadata about an entity type, for use
  * in the admin pages and elsewhere.
  *
  * @param entityType  The entity type
  * @param name        The human-readable name of the entity type
  * @param description A description of the entity type
  * @param created     The date and time the entity type was created
  * @param updated     The date and time the entity type was last updated
  */
case class EntityTypeMetadata(
  entityType: EntityType.Value,
  name: String,
  description: Option[String] = None,
  created: Option[Instant] = None,
  updated: Option[Instant] = None
)

object EntityTypeMetadata {
  implicit val _format: play.api.libs.json.Format[EntityTypeMetadata] = play.api.libs.json.Json.format[EntityTypeMetadata]
}

case class EntityTypeMetadataInfo(
  name: String,
  description: Option[String] = None
)

object EntityTypeMetadataInfo {
  implicit val _format: play.api.libs.json.Format[EntityTypeMetadataInfo] = play.api.libs.json.Json.format[EntityTypeMetadataInfo]
}
