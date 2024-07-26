package services.datamodel

import com.google.inject.ImplementedBy
import models._

import scala.collection.immutable.ListMap
import scala.concurrent.Future

@ImplementedBy(classOf[SqlEntityTypeMetadataService])
trait EntityTypeMetadataService {

  /**
    * Lists all entity type metadata entries.
    *
    * @return A map of entity type metadata entries
    */
  def list(): Future[Map[EntityType.Value, EntityTypeMetadata]]

  /**
    * Retrieves an entity type metadata entry.
    *
    * @param entityType The entity type
    * @return The entity type metadata entry, if found
    */
  def get(entityType: EntityType.Value): Future[Option[EntityTypeMetadata]]

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

  /**
    * Lists all field metadata entries.
    *
    * @param entityType The entity type to filter by
    * @return A map of entity type and field metadata entries, ordered according
    *         to the templates
    */
  def listFields(entityType: Option[EntityType.Value] = None): Future[Map[EntityType.Value, FieldMetadataSet]]

  /**
    * List field metadata for a specific entity type.
    */
  def listEntityTypeFields(entityType: EntityType.Value): Future[FieldMetadataSet]

  /**
    * Retrieves a field metadata entry.
    *
    * @param entityType The entity type
    * @param id The field id
    * @return The field metadata entry, if found
    */
  def getField(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]]

  /**
    * Saves a field metadata entry.
    *
    * @param entityType The entity type
    * @param id The field id
    * @param info The field metadata info
    * @return The saved field metadata
    */
  def saveField(entityType: EntityType.Value, id: String, info: FieldMetadataInfo): Future[FieldMetadata]

  /**
    * Deletes a field metadata entry.
    * @param entityType The entity type
    * @param id The field id
    * @return True if the field was deleted, false otherwise
    */
  def deleteField(entityType: EntityType.Value, id: String): Future[Boolean]

  /**
    * Returns an ordered map of the available fields, group by section,
    * for each supported entity type.
    */
  def templates(): Future[ListMap[EntityType.Value, ListMap[String, Seq[String]]]] = Future.successful(ListMap(
    // NB: Currently hard-coded, but could be loaded from the database
    EntityType.Country -> ListMap(CountryF.FIELDS: _*),
    EntityType.Repository -> ListMap(Isdiah.FIELDS: _*),
    EntityType.DocumentaryUnit -> ListMap(IsadG.FIELDS: _*),
    EntityType.AuthoritativeSet -> ListMap(AuthoritativeSetF.FIELDS: _*),
    EntityType.HistoricalAgent -> ListMap(Isaar.FIELDS: _*),
    EntityType.Vocabulary -> ListMap(VocabularyF.FIELDS: _*),
    EntityType.Concept -> ListMap(ConceptF.FIELDS: _*),
  ))
}
