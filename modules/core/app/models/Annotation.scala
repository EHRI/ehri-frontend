package models

import models.base._
import defines.{ContentTypes, EntityType}
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import backend._
import play.api.libs.json.JsObject


object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"
  val IS_PRIVATE = "isPrivate"

  object AnnotationType extends Enumeration {
    type Type = Value
    val Comment = Value("comment")
    val Aggregation = Value("aggregation")

    implicit val _format: Format[AnnotationType.Value] = defines.EnumUtils.enumFormat(this)
  }

  import AnnotationF.{ANNOTATION_TYPE => ANNOTATION_TYPE_PROP}
  import Entity._
  import Ontology._

  implicit val annotationFormat: Format[AnnotationF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Annotation) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ ANNOTATION_TYPE_PROP).formatNullableWithDefault(Some(AnnotationType.Comment)) and
    (__ \ DATA \ BODY).format[String] and
    (__ \ DATA \ FIELD).formatNullable[String] and
    (__ \ DATA \ COMMENT).formatNullable[String] and
    (__ \ DATA \ IS_PROMOTABLE).formatWithDefault(false)
  )(AnnotationF.apply, unlift(AnnotationF.unapply))

  implicit object Converter extends Writable[AnnotationF] {
    lazy val restFormat: Format[AnnotationF] = annotationFormat
    lazy val clientFormat: Format[AnnotationF] = Json.format[AnnotationF]
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
  import Entity._
  import Ontology._
  import defines.EnumUtils.enumMapping

  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = UserProfile.UserProfileResource.restReads
  private lazy implicit val systemEventReads = SystemEvent.SystemEventResource.restReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[Annotation] = (
    __.read[AnnotationF] and
    (__ \ RELATIONSHIPS \ ANNOTATION_ANNOTATES).lazyReadSeqOrEmpty[Annotation](metaReads) and
    (__ \ RELATIONSHIPS \ ANNOTATOR_HAS_ANNOTATION).readHeadNullable[UserProfile] and
    (__ \ RELATIONSHIPS \ ANNOTATION_HAS_SOURCE).lazyReadHeadNullable[AnyModel](anyModelReads) and
    (__ \ RELATIONSHIPS \ ANNOTATES).lazyReadHeadNullable[AnyModel](anyModelReads) and
    (__ \ RELATIONSHIPS \ ANNOTATES_PART).readHeadNullable[Entity] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty[Accessor] and
    (__ \ RELATIONSHIPS \ PROMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ DEMOTED_BY).readSeqOrEmpty[UserProfile] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Annotation.apply _)

  implicit object AnnotationResource extends backend.ContentType[Annotation]  {
    val entityType = EntityType.Annotation
    val contentType = ContentTypes.Annotation
    val restReads: Reads[Annotation] = metaReads
  }

  /**
   * Filter annotations on individual fields
   */
  def fieldAnnotations(partId: Option[String], annotations: Seq[Annotation]): Seq[Annotation] =
    annotations.filter(_.targetParts.exists(p => partId.contains(p.id))).filter(_.model.field.isDefined)

  /**
   * Filter annotations on the item
   */
  def itemAnnotations(partId: Option[String], annotations: Seq[Annotation]): Seq[Annotation] =
    annotations.filter(_.targetParts.exists(p => partId.contains(p.id)))

  /**
   * Filter annotations on the item
   */
  def itemAnnotations(annotations: Seq[Annotation]): Seq[Annotation] =
      annotations.filter(_.targetParts.isEmpty).filter(_.model.field.isDefined)

  import AnnotationF.{ANNOTATION_TYPE => ANNOTATION_TYPE_PROP, _}

  val form = Form(mapping(
    ISA -> ignored(EntityType.Annotation),
    ID -> optional(nonEmptyText),
    ANNOTATION_TYPE_PROP -> optional(enumMapping(AnnotationType)),
    BODY -> nonEmptyText(maxLength = 600),
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText),
    // NB: The object itself has an isPromotable flag, but the
    // form uses reverse semantics, forcing the user to un-check
    // the isPrivate (default: true) flag.
    IS_PRIVATE -> default(boolean.transform[Boolean](f => !f, f => !f), true)
  )(AnnotationF.apply)(AnnotationF.unapply))

  val multiForm = Form(single(
    "annotation" -> seq(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping
    ))
  ))
}

case class Annotation(
  model: AnnotationF,
  annotations: Seq[Annotation] = Nil,
  user: Option[UserProfile] = None,
  source: Option[AnyModel] = None,
  target: Option[AnyModel] = None,
  targetParts: Option[Entity] = None,
  accessors: Seq[Accessor] = Nil,
  promoters: Seq[UserProfile] = Nil,
  demoters: Seq[UserProfile] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends MetaModel[AnnotationF] with Accessible with Promotable {

  def isPromotable: Boolean = model.isPromotable
  def isOwnedBy(userOpt: Option[UserProfile]): Boolean = {
    (for {
      u <- userOpt
      creator <-user
    } yield u.id == creator.id).getOrElse(false)
  }

  def formatted: String = {
    s"${model.comment.map(c => s"$c\n\n").getOrElse("")}${model.body}"
  }
}