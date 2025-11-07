package models

import play.api.Logger
import play.api.libs.json._

import scala.collection.immutable.ListMap

sealed trait ValidationError {
  def id: String
}
case class MissingMandatoryField(id: String) extends ValidationError
case class MissingDesirableField(id: String) extends ValidationError

case class FieldMetadataSet(fieldMetadata: ListMap[String, FieldMetadata]) {
  private val logger: Logger = Logger(this.getClass)

  def toSeq: Seq[FieldMetadata] = fieldMetadata.values.toSeq

  def get(id: String): Option[FieldMetadata] = fieldMetadata.get(id)

  def nonEmpty: Boolean = fieldMetadata.nonEmpty

  def grouped: Seq[(Option[String], Iterable[FieldMetadata])] = {
    import utils.collections.GroupByOrderedImplicitImpl
    fieldMetadata.values.groupByOrdered(_.category)
  }

  def withCategory(cat: String): Seq[FieldMetadata] = fieldMetadata.values.filter(_.category.contains(cat)).toSeq

  def noCategory: Seq[FieldMetadata] = fieldMetadata.values.filter(_.category.isEmpty).toSeq

  def validate(entity: Entity, relationMap: Map[String, String] = Map.empty): Seq[ValidationError] = {
    def flattenEntity(e: Entity): Seq[String] = {
      // gather all populated keys in the entity and its child relationships. This
      // might lead to some missed attributes, because keys may be shared. But it's
      // good enough for now.
      e.data.keys.toSeq ++
        e.relationships.keys.flatMap(k => relationMap.get(k).toSeq) ++
        e.relationships.values.toSeq.flatten.flatMap { r => flattenEntity(r) }
    }

    val fms = fieldMetadata.values.filter(_.entityType == entity.isA).toSeq
    val allKeys = flattenEntity(entity)

    fms.filter(_.usage.contains(FieldMetadata.Usage.Mandatory)).flatMap { fm =>
      if (!allKeys.contains(fm.id)) Some(MissingMandatoryField(fm.id))
      else None
    } ++ fms.filter(_.usage.contains(FieldMetadata.Usage.Desirable)).flatMap { fm =>
      if (!allKeys.contains(fm.id)) Some(MissingDesirableField(fm.id))
      else None
    }
  }

  def validate[T <: Persistable: Writable](data: T): Seq[ValidationError] = {
    val relationMap = Persistable.getRelationToAttributeMap(data)
    val json = Json.toJson(data)(implicitly[Writable[T]]._format)
    json.validate[Entity] match {
      case JsSuccess(entity, _) => validate(entity, relationMap)
      case JsError(errors) =>
        logger.error(s"FieldMetadataSet.validate: failed to parse entity data: ${Json.prettyPrint(json)}: $errors")
        Seq.empty
    }
  }
}

object FieldMetadataSet {
  implicit val _writes: Writes[FieldMetadataSet] = Writes(fieldMetadataSet => {
    import play.api.libs.json.Json
    Json.toJson(fieldMetadataSet.fieldMetadata.values.toSeq)
  })
  implicit val _reads: Reads[FieldMetadataSet] = Reads(json => {
    import play.api.libs.json.Json
    Json.fromJson[Seq[FieldMetadata]](json).map(f => FieldMetadataSet(ListMap(f.map(fm => fm.id -> fm): _*)))
  })
  implicit val _format: Format[FieldMetadataSet] = Format(_reads, _writes)
}
