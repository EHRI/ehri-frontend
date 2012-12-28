package forms

import play.api.data._

import play.api.data.Forms._

import models._
import models.base.Field._

object DocumentaryUnitForm {

  import DocumentaryUnit._
  import DocumentaryUnitDescription._
  import IsadG._

  val form = Form(
      mapping(
    		Entity.ID -> optional(nonEmptyText),
    		Entity.IDENTIFIER -> nonEmptyText,
    		NAME -> nonEmptyText,
    		PUB_STATUS -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    Entity.ID -> optional(nonEmptyText),
    		    "languageCode" -> nonEmptyText,
    		    TITLE -> optional(nonEmptyText),
    		    "context" -> mapping(
    		        ADMIN_BIOG -> optional(text),
    		        ARCH_HIST -> optional(text),
    		        ACQUISITION -> optional(text)
    		    )(Context.apply)(Context.unapply),
    		    "content" -> mapping(
    		        SCOPE_CONTENT -> optional(text),
    		        APPRAISAL -> optional(text),
    		        ACCRUALS -> optional(text),
    		        SYS_ARR -> optional(text)
    		    )(Content.apply)(Content.unapply),
    		    "conditions" -> mapping(
    		        ACCESS_COND -> optional(text),
    		        REPROD_COND -> optional(text),
    		        PHYSICAL_CHARS -> optional(text),
    		        FINDING_AIDS -> optional(text)
    		    )(Conditions.apply)(Conditions.unapply),
    		    "materials" -> mapping(
    		        LOCATION_ORIGINALS -> optional(text),
    		        LOCATION_COPIES -> optional(text),
    		        RELATED_UNITS -> optional(text),
    		        PUBLICATION_NOTE -> optional(text)
    		    )(Materials.apply)(Materials.unapply)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  )
}
