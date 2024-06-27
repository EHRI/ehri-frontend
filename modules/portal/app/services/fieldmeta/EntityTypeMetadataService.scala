package services.fieldmeta

import com.google.inject.ImplementedBy
import models._

import scala.concurrent.Future

@ImplementedBy(classOf[SqlEntityTypeMetadataService])
trait EntityTypeMetadataService {

  /**
    * Lists all entity type metadata entries.
    *
    * @param entityType The entity type to filter by
    * @return A map of entity type metadata entries
    */
  def list(entityType: Option[EntityType.Value] = None): Future[Map[EntityType.Value, EntityTypeMetadata]]

  /**
    * Saves a field metadata entry.
    *
    * @param entityType The entity type
    * @param info The field metadata info
    * @return The saved field metadata
    */
  def save(entityType: EntityType.Value, info: EntityTypeMetadataInfo): Future[EntityTypeMetadata]

  /**
    * Deletes a field metadata entry.
    * @param entityType The entity type
    * @return True if the field was deleted, false otherwise
    */
  def delete(entityType: EntityType.Value): Future[Boolean]
}
