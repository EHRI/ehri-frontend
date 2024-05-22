package services.fieldmeta

import com.google.inject.ImplementedBy
import models.{EntityType, FieldMetadata}

import scala.concurrent.Future

@ImplementedBy(classOf[SqlFieldMetadataService])
trait FieldMetadataService {

  def list(entityType: Option[EntityType.Value] = None): Future[Seq[FieldMetadata]]

  def get(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]]

  def create(fieldMeta: FieldMetadata): Future[FieldMetadata]

  def update(fieldMeta: FieldMetadata): Future[FieldMetadata]

  def delete(entityType: EntityType.Value, id: String): Future[Boolean]
}
