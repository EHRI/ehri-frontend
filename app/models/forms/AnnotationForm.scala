package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{AnnotationF, Entity}

/**
 * User: michaelb
 */
object AnnotationForm {

  import AnnotationF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    ANNOTATION_TYPE -> optional(models.forms.enum(AnnotationType)),
    BODY -> nonEmptyText, // TODO: Validate this server side
    FIELD -> optional(nonEmptyText),
    COMMENT -> optional(nonEmptyText)
  )(AnnotationF.apply)(AnnotationF.unapply))

  val multiForm = Form(    single(
    "annotation" -> list(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping
    ))
  ))
}