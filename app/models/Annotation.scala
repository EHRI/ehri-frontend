package models

import base.{AnnotatableEntity, AccessibleEntity, Accessor, Formable}
import org.joda.time.DateTime

import models.forms.{AnnotationType,AnnotationF}
import defines.enum
import play.api.i18n.Messages

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

