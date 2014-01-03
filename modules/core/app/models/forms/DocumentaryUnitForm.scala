package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{Entity, DocumentaryUnitF}
import play.api.libs.json.{Json, JsNull}
import defines.EntityType

/**
 * Documentary unit model form.
 */
object DocumentaryUnitForm {

  import DocumentaryUnitF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.DocumentaryUnit),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      OTHER_IDENTIFIERS -> optional(list(nonEmptyText)),
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      COPYRIGHT -> optional(models.forms.enum(CopyrightStatus)),
      SCOPE -> optional(models.forms.enum(Scope)),
      "descriptions" -> list(IsadGForm.form.mapping)
    )(DocumentaryUnitF.apply)(DocumentaryUnitF.unapply)
  )
}
