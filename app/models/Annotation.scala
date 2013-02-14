package models

import base.{AnnotatableEntity, AccessibleEntity, Accessor, Formable}
import org.joda.time.DateTime

import defines.enum
import play.api.i18n.Messages

import play.api.data.Form
import play.api.data.Forms._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites

object AnnotationType extends Enumeration {
  type Type = Value
  val Comment = Value("comment")
  val Link = Value("link")
  val Aggregation = Value("aggregation")

}

object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"
}

case class AnnotationF(
  val id: Option[String],
  val annotationType: AnnotationType.Type,
  val body: String,
  val field: Option[String] = None,
  val comment: Option[String] = None
) extends Persistable {
  val isA = EntityType.Annotation

  def toJson = {
    implicit val dateWrites = play.api.libs.json.Writes.jodaDateWrites("yyyy-MM-dd")
    import AnnotationF._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        BODY -> body,
        ANNOTATION_TYPE -> annotationType,
        FIELD -> field,
        COMMENT -> comment
      )
    )
  }
}


object AnnotationForm {

  import AnnotationF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    ANNOTATION_TYPE -> models.forms.enum(AnnotationType),
    BODY -> nonEmptyText, // TODO: Validate this server side
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText)
  )(AnnotationF.apply)(AnnotationF.unapply))
}

object Annotation {
  final val ANNOTATES_REL = "annotates"
  final val ACCESSOR_REL = "hasAnnotation"
  final val SOURCE_REL = "hasSource"
}

case class Annotation(val e: Entity) extends AccessibleEntity
  with AnnotatableEntity
  with Formable[AnnotationF] {

  def annotations: List[Annotation] = e.relations(Annotation.ANNOTATES_REL).map(Annotation(_))
  def accessor: Option[Accessor] = e.relations(Annotation.ACCESSOR_REL).headOption.map(Accessor(_))
  def source: Option[AnnotatableEntity] = e.relations(Annotation.SOURCE_REL).headOption.flatMap(AnnotatableEntity.fromEntity(_))

  /**
   * Output a formatted label representation. TODO: Improve.
   * @return
   */
  def formatted: String = {
    "%s%s%s".format(
      accessor.map(a => s"${a}\n\n").getOrElse(""),
      to.body,
      to.comment.map(c => s"$c\n\n").getOrElse("")
    )
  }

  def to: AnnotationF = new AnnotationF(
    id = Some(e.id),
    // NB: For the time being, annotations with no 'type' default to being a comment
    annotationType = e.property(AnnotationF.ANNOTATION_TYPE).flatMap(enum(AnnotationType).reads(_).asOpt).getOrElse(AnnotationType.Comment),
    body = e.stringProperty(AnnotationF.BODY).getOrElse(Messages("annotation.emptyBodyText")),
    field = e.stringProperty(AnnotationF.FIELD),
    comment = e.stringProperty(AnnotationF.COMMENT)
  )
}

