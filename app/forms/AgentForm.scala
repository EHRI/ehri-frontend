package forms

import play.api.data._
import play.api.data.Forms._

import models._

object AgentForm {

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
    		    "generalContext" -> optional(text)
    		  )(AgentDescription.apply)(AgentDescription.unapply)
    		)
      )(Agent.apply)(Agent.unform)
  ) 
}
