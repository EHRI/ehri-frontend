package forms

import play.api.data._
import play.api.data.Forms._

import models._

object DocumentaryUnitForm {

  val form = Form(
      mapping(
    		"identifier" -> nonEmptyText,
    		"publicationStatus" -> optional(enum(defines.PublicationStatus)),
    		"descriptions" -> list(
    		  mapping(
    		    "languageCode" -> nonEmptyText,
    		    "title" -> optional(text),
    		    "scopeAndContent" -> optional(text)
    		  )(DocumentaryUnitDescription.apply)(DocumentaryUnitDescription.unapply)
            )
      )(DocumentaryUnit.apply)(DocumentaryUnit.unapply)
  ) 
}
