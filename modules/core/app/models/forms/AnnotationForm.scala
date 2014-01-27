package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{AnnotationF, Entity}
import defines.EntityType
import eu.ehri.project.definitions.Ontology

/**
 * User: michaelb
 */
object AnnotationForm {

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