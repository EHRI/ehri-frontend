package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models.{SystemEvent, UserProfile, Annotation, AnnotationF}
import defines.EntityType
import defines.EnumUtils._
import models.base.{AnyModel, Accessible, Accessor, MetaModel}
import eu.ehri.project.definitions.Ontology

object AnnotationFormat {
  import AnnotationF.{ANNOTATION_TYPE => ANNOTATION_TYPE_PROP, _}
  import models.Entity._
  import Ontology._

  implicit val annotationTypeReads = enumReads(AnnotationType)

  implicit val annotationWrites: Writes[AnnotationF] = new Writes[AnnotationF] {
    def writes(d: AnnotationF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          ANNOTATION_TYPE_PROP -> d.annotationType,
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
      ((__ \ DATA \ ANNOTATION_TYPE_PROP).readNullable[AnnotationType.Value]
          orElse Reads.pure(Some(AnnotationType.Comment))) and
      (__ \ DATA \ BODY).read[String] and
      (__ \ DATA \ FIELD).readNullable[String] and
      (__ \ DATA \ COMMENT).readNullable[String]
    )(AnnotationF.apply _)

  implicit val restFormat: Format[AnnotationF] = Format(annotationReads,annotationWrites)

  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = UserProfileFormat.metaReads
  private lazy implicit val systemEventReads = SystemEventFormat.metaReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[Annotation] = (
    __.read[AnnotationF] and
    (__ \ RELATIONSHIPS \ ANNOTATION_ANNOTATES).lazyReadNullable[List[Annotation]](
         Reads.list(metaReads)).map(_.getOrElse(List.empty[Annotation])) and
    (__ \ RELATIONSHIPS \ ANNOTATOR_HAS_ANNOTATION).lazyReadNullable[List[UserProfile]](
      Reads.list(userProfileMetaReads)).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ ANNOTATION_HAS_SOURCE).lazyReadNullable[List[AnyModel]](
      Reads.list(anyModelReads)).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption))
    )(Annotation.apply _)
}