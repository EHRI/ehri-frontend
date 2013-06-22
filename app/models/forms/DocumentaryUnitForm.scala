package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{Entity, DocumentaryUnitF}
import models.base.DescribedEntity
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
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      COPYRIGHT -> optional(models.forms.enum(CopyrightStatus)),
      SCOPE -> optional(models.forms.enum(Scope)),
      DescribedEntity.DESCRIPTIONS -> list(IsadGForm.form.mapping)
    )(DocumentaryUnitF.apply)(DocumentaryUnitF.unapply)
  )
}
