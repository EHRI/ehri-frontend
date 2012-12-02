package forms

import play.api.data._

import play.api.data.Forms._

import models._
import models.base.Field._

object DocumentaryUnitForm {

  val form = Form(
      mapping(
    		Entity.ID -> optional(text),
    		Entity.IDENTIFIER -> nonEmptyText,
    		DocumentaryUnit.NAME.id -> nonEmptyText,
    		DocumentaryUnit.PUB_STATUS.id -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    Entity.ID -> optional(text),
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
    		    )(DocumentaryUnitContent.apply)(DocumentaryUnitContent.unapply),
    		    "conditions" -> mapping(
    		        DocumentaryUnit.ACCESS_COND.id -> optional(text),
    		        DocumentaryUnit.REPROD_COND.id -> optional(text),
    		        DocumentaryUnit.PHYSICAL_CHARS.id -> optional(text),
    		        DocumentaryUnit.FINDING_AIDS.id -> optional(text)
    		    )(DocumentaryUnitConditions.apply)(DocumentaryUnitConditions.unapply),
    		    "materials" -> mapping(
    		        DocumentaryUnit.LOCATION_ORIGINALS.id -> optional(text),
    		        DocumentaryUnit.LOCATION_COPIES.id -> optional(text),
    		        DocumentaryUnit.RELATED_UNITS.id -> optional(text),
    		        DocumentaryUnit.PUBLICATION_NOTE.id -> optional(text)
    		    )(DocumentaryUnitMaterials.apply)(DocumentaryUnitMaterials.unapply)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  ) 
}
