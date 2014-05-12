package models

import models.base._
import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import defines.EnumUtils._
import play.api.libs.json.JsObject


object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"
  val ALLOW_PUBLIC = Ontology.IS_PROMOTABLE

  object AnnotationType extends Enumeration {
    type Type = Value
    val Comment = Value("comment")
    val Aggregation = Value("aggregation")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

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
          COMMENT -> d.comment,
          IS_PROMOTABLE -> d.isPromotable
        )
      )
    }
  }

  implicit val annotationReads: Reads[AnnotationF] = (
    (__ \ TYPE).readIfEquals(EntityType.Annotation) and
    (__ \ ID).readNullable[String] and
    ((__ \ DATA \ ANNOTATION_TYPE_PROP).readNullable[AnnotationType.Value]
      orElse Reads.pure(Some(AnnotationType.Comment))) and
    (__ \ DATA \ BODY).read[String] and
    (__ \ DATA \ FIELD).readNullable[String] and
    (__ \ DATA \ COMMENT).readNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).readNullable[Boolean].map(_.getOrElse(false))
  )(AnnotationF.apply _)

  implicit val annotationFormat: Format[AnnotationF] = Format(annotationReads,annotationWrites)

  implicit object Converter extends RestConvertable[AnnotationF] with ClientConvertable[AnnotationF] {
    lazy val restFormat = annotationFormat
    lazy val clientFormat = Json.format[AnnotationF]
  }
}

case class AnnotationF(
  isA: EntityType.Value = EntityType.Annotation,
  id: Option[String],
  annotationType: Option[AnnotationF.AnnotationType.Type] = Some(AnnotationF.AnnotationType.Comment),
  body: String,
  field: Option[String] = None,
  comment: Option[String] = None,
  isPromotable: Boolean = false
) extends Model with Persistable


object Annotation {
  import models.Entity._
  import Ontology._

  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = UserProfile.Converter.restReads
  private lazy implicit val systemEventReads = SystemEvent.Converter.restReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[Annotation] = (
    __.read[AnnotationF] and
      (__ \ RELATIONSHIPS \ ANNOTATION_ANNOTATES).lazyReadNullable[List[Annotation]](
        Reads.list(metaReads)).map(_.getOrElse(List.empty[Annotation])) and
      (__ \ RELATIONSHIPS \ ANNOTATOR_HAS_ANNOTATION).lazyReadNullable[List[UserProfile]](
        Reads.list(userProfileMetaReads)).map(_.flatMap(_.headOption)) and
      (__ \ RELATIONSHIPS \ ANNOTATION_HAS_SOURCE).lazyReadNullable[List[AnyModel]](
        Reads.list(anyModelReads)).map(_.flatMap(_.headOption)) and
      (__ \ RELATIONSHIPS \ ANNOTATES).lazyReadNullable[List[AnyModel]](
        Reads.list(anyModelReads)).map(_.flatMap(_.headOption)) and
      (__ \ RELATIONSHIPS \ ANNOTATES_PART).lazyReadNullable[List[Entity]](
        Reads.list(Entity.entityReads)).map(_.flatMap(_.headOption)) and
      (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
        Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
      (__ \ RELATIONSHIPS \ PROMOTED_BY).lazyReadNullable[List[UserProfile]](
        Reads.list(UserProfile.Converter.restReads)).map(_.getOrElse(List.empty[UserProfile])) and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
        Reads.list[SystemEvent]).map(_.flatMap(_.headOption)) and
      (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
    )(Annotation.apply _)

  implicit object Converter extends ClientConvertable[Annotation] with RestReadable[Annotation] {
    val restReads = metaReads

    val clientFormat: Format[Annotation] = (
      __.format[AnnotationF](AnnotationF.Converter.clientFormat) and
      (__ \ "annotations").lazyNullableListFormat(clientFormat) and
      (__ \ "user").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
      (__ \ "source").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
      (__ \ "target").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
      (__ \ "targetPart").lazyFormatNullable[Entity](Entity.entityFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "promotedBy").nullableListFormat(UserProfile.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Annotation.apply _, unlift(Annotation.unapply))
  }

  implicit object Resource extends RestResource[Annotation] {
    val entityType = EntityType.Annotation
  }

  /**
   * Filter annotations on individual fields
   */
  def fieldAnnotations(partId: Option[String], annotations: Seq[Annotation]): Seq[Annotation] =
    annotations.filter(_.targetParts.exists(p => Some(p.id) == partId)).filter(_.model.field.isDefined)

  /**
   * Filter annotations on the item
   */
  def itemAnnotations(partId: Option[String], annotations: Seq[Annotation]): Seq[Annotation] =
    annotations.filter(_.targetParts.exists(p => Some(p.id) == partId))

  /**
   * Filter annotations on the item
   */
  def itemAnnotations(annotations: Seq[Annotation]): Seq[Annotation] =
      annotations.filter(_.targetParts.isEmpty).filter(_.model.field.isDefined)

  import AnnotationF.{ANNOTATION_TYPE => ANNOTATION_TYPE_PROP, _}

  val form = Form(mapping(
    ISA -> ignored(EntityType.Annotation),
    ID -> optional(nonEmptyText),
    ANNOTATION_TYPE_PROP -> optional(models.forms.enum(AnnotationType)),
    BODY -> nonEmptyText(maxLength = 600),
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText),
    Ontology.IS_PROMOTABLE -> default(boolean, false)
  )(AnnotationF.apply)(AnnotationF.unapply))

  val multiForm = Form(single(
    "annotation" -> list(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping
    ))
  ))
}

case class Annotation(
  model: AnnotationF,
  annotations: List[Annotation] = Nil,
  user: Option[UserProfile] = None,
  source: Option[AnyModel] = None,
  target: Option[AnyModel] = None,
  targetParts: Option[Entity] = None,
  accessors: List[Accessor] = Nil,
  promotors: List[UserProfile] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends MetaModel[AnnotationF] with Accessible with Promotable {

  def isOwnedBy(userOpt: Option[UserProfile]): Boolean = {
    (for {
      u <- userOpt
      creator <-user
    } yield u.id == creator.id).getOrElse(false)
  }

  def formatted: String = {
    "%s%s".format(
      model.comment.map(c => s"$c\n\n").getOrElse(""),
      model.body
    )
  }
}