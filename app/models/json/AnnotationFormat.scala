package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models.AnnotationF
import defines.EntityType
import defines.EnumUtils._

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
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Annotation)) andKeep
    (__ \ ID).readNullable[String] and
      ((__ \ DATA \ ANNOTATION_TYPE).readNullable[AnnotationType.Value]
          orElse Reads.pure(Some(AnnotationType.Comment))) and
      (__ \ DATA \ BODY).read[String] and
      (__ \ DATA \ FIELD).readNullable[String] and
      (__ \ DATA \ COMMENT).readNullable[String]
    )(AnnotationF.apply _)

  implicit val annotationFormat: Format[AnnotationF] = Format(annotationReads,annotationWrites)
}