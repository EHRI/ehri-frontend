package forms

import play.api.data._
import play.api.data.Forms._

import models._

object DocumentaryUnitForm {

  val form = Form(
      mapping(
    		Entity.ID -> optional(longNumber),
    		Entity.IDENTIFIER -> nonEmptyText,
    		DocumentaryUnit.NAME -> nonEmptyText,
    		DocumentaryUnit.PUB_STATUS -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    Entity.ID -> optional(longNumber),
    		    "languageCode" -> nonEmptyText,
    		    DocumentaryUnit.TITLE -> optional(text),
    		    "context" -> mapping(
    		        DocumentaryUnit.ADMIN_BIOG -> optional(text),
    		        DocumentaryUnit.ARCH_HIST -> optional(text),
    		        DocumentaryUnit.ACQUISITION -> optional(text)
    		    )(DocumentaryUnitContext.apply)(DocumentaryUnitContext.unapply),
    		    DocumentaryUnit.SCOPE_CONTENT -> optional(text)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  ) 
}
