package forms

import play.api.data._

import play.api.data.Forms._

import models._
import models.base.Field._

object DocumentaryUnitForm {

  import DocumentaryUnit._
  
  val form = Form(            
      mapping(
    		Entity.ID -> optional(nonEmptyText),
    		Entity.IDENTIFIER -> nonEmptyText,
    		NAME.id -> nonEmptyText,
    		PUB_STATUS.id -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    Entity.ID -> optional(nonEmptyText),
    		    "languageCode" -> nonEmptyText,
    		    DocumentaryUnit.TITLE.id -> optional(nonEmptyText),
    		    "context" -> mapping(
    		        ADMIN_BIOG.id -> optional(text),
    		        ARCH_HIST.id -> optional(text),
    		        ACQUISITION.id -> optional(text)
    		    )(DocumentaryUnitContext.apply)(DocumentaryUnitContext.unapply),
    		    "content" -> mapping(
    		        SCOPE_CONTENT.id -> optional(text),
    		        APPRAISAL.id -> optional(text),
    		        ACCRUALS.id -> optional(text),
    		        SYS_ARR.id -> optional(text)
    		    )(DocumentaryUnitContent.apply)(DocumentaryUnitContent.unapply),
    		    "conditions" -> mapping(
    		        ACCESS_COND.id -> optional(text),
    		        REPROD_COND.id -> optional(text),
    		        PHYSICAL_CHARS.id -> optional(text),
    		        FINDING_AIDS.id -> optional(text)
    		    )(DocumentaryUnitConditions.apply)(DocumentaryUnitConditions.unapply),
    		    "materials" -> mapping(
    		        LOCATION_ORIGINALS.id -> optional(text),
    		        LOCATION_COPIES.id -> optional(text),
    		        RELATED_UNITS.id -> optional(text),
    		        PUBLICATION_NOTE.id -> optional(text)
    		    )(DocumentaryUnitMaterials.apply)(DocumentaryUnitMaterials.unapply)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  ) 
}
