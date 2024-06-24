package services.fieldmeta

import com.google.inject.ImplementedBy
import models.{EntityType, FieldMetadata, FieldMetadataInfo}

import scala.concurrent.Future

@ImplementedBy(classOf[SqlFieldMetadataService])
trait FieldMetadataService {

  def list(entityType: Option[EntityType.Value] = None): Future[Seq[(EntityType.Value, Seq[FieldMetadata])]]

  def get(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]]

  def save(entityType: EntityType.Value, id: String, fieldMeta: FieldMetadataInfo): Future[FieldMetadata]

  def delete(entityType: EntityType.Value, id: String): Future[Boolean]
}
