package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{AccessPointF, Entity}
import defines.EntityType

/**
 * User: michaelb
 */
object AccessPointForm {

  import AccessPointF._

  val form = Form(mapping(
    Entity.ID -> optional(nonEmptyText),
    TYPE -> models.forms.enum(AccessPointType),
    TARGET -> nonEmptyText, // TODO: Validate this server side
    DESCRIPTION -> optional(nonEmptyText)
  )(AccessPointF.apply)(AccessPointF.unapply))
}