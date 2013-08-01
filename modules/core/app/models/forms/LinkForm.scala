package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{LinkF, Entity}
import defines.EntityType

/**
 * User: michaelb
 */
object LinkForm {

  import LinkF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.Link),
    Entity.ID -> optional(nonEmptyText),
    LINK_TYPE -> models.forms.enum(LinkType),
    DESCRIPTION -> optional(nonEmptyText) // TODO: Validate this server side
  )(LinkF.apply)(LinkF.unapply))

  val multiForm = Form(    single(
    "link" -> list(tuple(
      "id" -> nonEmptyText,
      "data" -> form.mapping,
      "accessPoint" -> optional(nonEmptyText)
    ))
  ))
}