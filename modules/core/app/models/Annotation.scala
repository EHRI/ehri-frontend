package models

import models.base._
import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
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

  implicit object Converter extends RestConvertable[AnnotationF] with ClientConvertable[AnnotationF] {
    lazy val restFormat = models.json.AnnotationFormat.restFormat
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
  implicit object Converter extends ClientConvertable[Annotation] with RestReadable[Annotation] {
    val restReads = models.json.AnnotationFormat.metaReads

    val clientFormat: Format[Annotation] = (
      __.format[AnnotationF](AnnotationF.Converter.clientFormat) and
      lazyNullableListFormat(__ \ "annotations")(clientFormat) and
      (__ \ "user").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
      (__ \ "source").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
    (__ \ "target").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
      (__ \ "targetPart").lazyFormatNullable[Entity](models.json.entityFormat) and
      nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      nullableListFormat(__ \ "promotedBy")(UserProfile.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Annotation.apply _, unlift(Annotation.unapply _))
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

  import AnnotationF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.Annotation),
    Entity.ID -> optional(nonEmptyText),
    ANNOTATION_TYPE -> optional(models.forms.enum(AnnotationType)),
    BODY -> nonEmptyText(minLength = 15, maxLength = 600),
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText),
    Ontology.IS_PROMOTABLE -> default(boolean, false)
  )(AnnotationF.apply)(AnnotationF.unapply))

  val multiForm = Form(    single(
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
    } yield (u.id == creator.id)).getOrElse(false)
  }

  def formatted: String = {
    "%s%s".format(
      model.comment.map(c => s"$c\n\n").getOrElse(""),
      model.body
    )
  }
}