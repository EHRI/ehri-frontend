package models

import models.base._
import defines.EntityType
import models.json._
import scala.Some
import play.api.libs.json._
import scala.Some
import scala.Some
import scala.Some
import play.api.libs.functional.syntax._
import scala.Some
import backend.Visibility


object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"
  val ALLOW_PUBLIC = "public"

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
  allowPublic: Boolean = false
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
}

case class Annotation(
  model: AnnotationF,
  annotations: List[Annotation] = Nil,
  user: Option[UserProfile] = None,
  source: Option[AnyModel] = None,
  target: Option[AnyModel] = None,
  targetParts: Option[Entity] = None,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends MetaModel[AnnotationF] with Accessible {
  def formatted: String = {
    "%s%s".format(
      model.comment.map(c => s"$c\n\n").getOrElse(""),
      model.body
    )
  }
}