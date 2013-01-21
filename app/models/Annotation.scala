package models

import base.{AccessibleEntity, Accessor, Formable}
import org.joda.time.DateTime

import models.forms.{AnnotationType,AnnotationF}
import defines.enum

object Annotation {
  final val ANNOTATES_REL = "annotates"
  final val ACCESSOR_REL = "hasAnnotation"
  final val SOURCE_REL = "hasSource"
}

case class Annotation(val e: Entity) extends AccessibleEntity with Formable[AnnotationF] {

  def annotations: List[Annotation] = e.relations(Annotation.ANNOTATES_REL).map(Annotation(_))
  def accessor: Option[Accessor] = e.relations(Annotation.ACCESSOR_REL).headOption.map(Accessor(_))
  def source: Option[ItemWithId] = e.relations(Annotation.SOURCE_REL).headOption.map(ItemWithId(_))

  def to: AnnotationF = new AnnotationF(
    id = Some(e.id),
    // NB: For the time being, annotations with no 'type' default to being a comment
    annotationType = e.property(AnnotationF.ANNOTATION_TYPE).flatMap(enum(AnnotationType).reads(_).asOpt).getOrElse(AnnotationType.Comment),
    body = e.stringProperty(AnnotationF.BODY),
    field = e.stringProperty(AnnotationF.FIELD),
    comment = e.stringProperty(AnnotationF.COMMENT)
  )
}

