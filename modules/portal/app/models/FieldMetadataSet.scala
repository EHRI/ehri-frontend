package models

import play.api.libs.json.{Format, Reads, Writes}

import scala.collection.immutable.ListMap

case class FieldMetadataSet(fieldMetadata: ListMap[String, FieldMetadata]) {
  def toSeq: Seq[FieldMetadata] = fieldMetadata.values.toSeq

  def get(id: String): Option[FieldMetadata] = fieldMetadata.get(id)

  def nonEmpty: Boolean = fieldMetadata.nonEmpty

  def grouped: Seq[(Option[String], Iterable[FieldMetadata])] = {
    import utils.collections.GroupByOrderedImplicitImpl
    fieldMetadata.values.groupByOrdered(_.category)
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
