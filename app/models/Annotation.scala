package models

import models.base.Formable
import org.joda.time.DateTime

import models.forms.{AnnotationType,AnnotationF}
import defines.enum

case class Annotation(val e: Entity) extends Formable[AnnotationF] {
  def to: AnnotationF = new AnnotationF(
    id = Some(e.id),
    // NB: For the time being, annotations with no 'type' default to being a comment
    annotationType = e.property(AnnotationF.ANNOTATION_TYPE).flatMap(enum(AnnotationType).reads(_).asOpt).getOrElse(AnnotationType.Comment),
    body = e.stringProperty(AnnotationF.BODY),
    field = e.stringProperty(AnnotationF.FIELD),
    comment = e.stringProperty(AnnotationF.COMMENT)
  )
}

