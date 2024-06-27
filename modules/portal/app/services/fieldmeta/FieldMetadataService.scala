package services.fieldmeta

import com.google.inject.ImplementedBy
import models._

import scala.collection.immutable.ListMap
import scala.concurrent.Future

@ImplementedBy(classOf[SqlFieldMetadataService])
trait FieldMetadataService {

  /**
    * Lists all field metadata entries.
    *
    * @param entityType The entity type to filter by
    * @return A map of entity type and field metadata entries, ordered according
    *         to the templates
    */
  def list(entityType: Option[EntityType.Value] = None): Future[Map[EntityType.Value, FieldMetadataSet]]

  /**
    * Retrieves a field metadata entry.
    *
    * @param entityType The entity type
    * @param id The field id
    * @return The field metadata entry, if found
    */
  def get(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]]

  /**
    * Saves a field metadata entry.
    *
    * @param entityType The entity type
    * @param id The field id
    * @param info The field metadata info
    * @return The saved field metadata
    */
  def save(entityType: EntityType.Value, id: String, info: FieldMetadataInfo): Future[FieldMetadata]

  /**
    * Deletes a field metadata entry.
    * @param entityType The entity type
    * @param id The field id
    * @return True if the field was deleted, false otherwise
    */
  def delete(entityType: EntityType.Value, id: String): Future[Boolean]

  /**
    * Returns an ordered map of the available fields, group by section,
    * for each supported entity type.
    */
  def templates(): Future[ListMap[EntityType.Value, Seq[(String, Seq[String])]]] = Future.successful(ListMap(
    // NB: Currently hard-coded, but could be loaded from the database
    EntityType.Country -> CountryF.FIELDS,
    EntityType.RepositoryDescription -> Isdiah.FIELDS,
    EntityType.DocumentaryUnitDescription -> IsadG.FIELDS,
    EntityType.HistoricalAgentDescription -> Isaar.FIELDS
  ))
}
