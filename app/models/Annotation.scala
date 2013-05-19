package models

import base.{AnnotatableEntity, AccessibleEntity, Accessor, Formable}
import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json


object AnnotationF {
  val BODY = "body"
  val FIELD = "field"
  val ANNOTATION_TYPE = "annotationType"
  val COMMENT = "comment"

  object AnnotationType extends Enumeration {
    type Type = Value
    val Comment = Value("comment")
    val Aggregation = Value("aggregation")
  }

  lazy implicit val annotationFormat = json.AnnotationFormat.annotationFormat
}

case class AnnotationF(
  val id: Option[String],
  val annotationType: Option[AnnotationF.AnnotationType.Type] = Some(AnnotationF.AnnotationType.Comment),
  val body: String,
  val field: Option[String] = None,
  val comment: Option[String] = None
) extends Persistable {
  val isA = EntityType.Annotation

  def toJson = Json.toJson(this)
}



object Annotation {
  final val ANNOTATES_REL = "hasAnnotationTarget"
  final val ACCESSOR_REL = "hasAnnotation"
  final val SOURCE_REL = "hasAnnotationBody"
}

case class Annotation(val e: Entity) extends AccessibleEntity
  with AnnotatableEntity
  with Formable[AnnotationF] {

  def annotations: List[Annotation] = e.relations(Annotation.ANNOTATES_REL).map(Annotation(_))
  def accessor: Option[Accessor] = e.relations(Annotation.ACCESSOR_REL).headOption.map(Accessor(_))
  def source: Option[AnnotatableEntity] = e.relations(Annotation.SOURCE_REL)
      .headOption.flatMap(AnnotatableEntity.fromEntity(_))

  /**
   * Output a formatted label representation. TODO: Improve.
   * @return
   */
  def formatted: String = {
    "%s%s".format(
      formable.comment.map(c => s"$c\n\n").getOrElse(""),
      formable.body
    )
  }

  lazy val formable: AnnotationF = Json.toJson(e).as[AnnotationF]
  lazy val formableOpt: Option[AnnotationF] = Json.toJson(e).asOpt[AnnotationF]
}

