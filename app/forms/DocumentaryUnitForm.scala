package forms

import play.api.data._

import play.api.data.Forms._

import models._
import models.base.Field._

object DocumentaryUnitForm {

  val form = Form(
      mapping(
    		Entity.ID -> optional(longNumber),
    		Entity.IDENTIFIER -> nonEmptyText,
    		DocumentaryUnit.NAME.id -> nonEmptyText,
    		DocumentaryUnit.PUB_STATUS.id -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    Entity.ID -> optional(longNumber),
    		    "languageCode" -> nonEmptyText,
    		    DocumentaryUnit.TITLE.id -> optional(text),
    		    "context" -> mapping(
    		        DocumentaryUnit.ADMIN_BIOG.id -> optional(text),
    		        DocumentaryUnit.ARCH_HIST.id -> optional(text),
    		        DocumentaryUnit.ACQUISITION.id -> optional(text)
    		    )(DocumentaryUnitContext.apply)(DocumentaryUnitContext.unapply),
    		    "content" -> mapping(
    		        DocumentaryUnit.SCOPE_CONTENT.id -> optional(text),
    		        DocumentaryUnit.APPRAISAL.id -> optional(text),
    		        DocumentaryUnit.ACCRUALS.id -> optional(text),
    		        DocumentaryUnit.SYS_ARR.id -> optional(text)
    		    )(DocumentaryUnitContent.apply)(DocumentaryUnitContent.unapply)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  ) 
}
