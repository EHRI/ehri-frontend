package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models.{SystemEventMeta, UserProfileMeta, AnnotationMeta, AnnotationF}
import defines.EntityType
import defines.EnumUtils._
import models.base.{Accessor, AccessibleEntity, MetaModel}

object AnnotationFormat {
  import AnnotationF._
  import models.Entity._

  implicit val annotationTypeReads = enumReads(AnnotationType)

  implicit val annotationWrites: Writes[AnnotationF] = new Writes[AnnotationF] {
    def writes(d: AnnotationF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          ANNOTATION_TYPE -> d.annotationType,
          BODY -> d.body,
          FIELD -> d.field,
          COMMENT -> d.comment
        )
      )
    }
  }

  implicit val annotationReads: Reads[AnnotationF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Annotation)) and
    (__ \ ID).readNullable[String] and
      ((__ \ DATA \ ANNOTATION_TYPE).readNullable[AnnotationType.Value]
          orElse Reads.pure(Some(AnnotationType.Comment))) and
      (__ \ DATA \ BODY).read[String] and
      (__ \ DATA \ FIELD).readNullable[String] and
      (__ \ DATA \ COMMENT).readNullable[String]
    )(AnnotationF.apply _)

  implicit val restFormat: Format[AnnotationF] = Format(annotationReads,annotationWrites)

  private implicit val metaModelReads = MetaModel.Converter.restReads
  private implicit val userProfileReads = UserProfileFormat.userProfileReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[AnnotationMeta] = (
    __.read[AnnotationF] and
    (__ \ AnnotationF.ANNOTATES_REL).lazyReadNullable[List[AnnotationMeta]](
         Reads.list(metaReads)).map(_.getOrElse(List.empty[AnnotationMeta])) and
    (__ \ AnnotationF.ACCESSOR_REL).readNullable[UserProfileMeta] and
    (__ \ AnnotationF.SOURCE_REL).readNullable[MetaModel[_]] and
    (__ \ RELATIONSHIPS \ AccessibleEntity.ACCESS_REL).lazyReadNullable[List[Accessor]](
      Reads.list[Accessor]).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list[SystemEventMeta]).map(_.flatMap(_.headOption))
    )(AnnotationMeta.apply _)
}