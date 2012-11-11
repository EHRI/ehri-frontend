package forms

import play.api.data._
import play.api.data.Forms._

import models._

object DocumentaryUnitForm {

  val form = Form(
      mapping(
    		"id" -> optional(longNumber),
    		"identifier" -> nonEmptyText,
    		"name" -> nonEmptyText,
    		"publicationStatus" -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    "id" -> optional(longNumber),
    		    "languageCode" -> nonEmptyText,
    		    "title" -> optional(text),
    		    "scopeAndContent" -> optional(text)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  ) 
}
