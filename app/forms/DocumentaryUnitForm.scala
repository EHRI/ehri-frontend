package forms

import play.api.data._
import play.api.data.Forms._

import models._

object DocumentaryUnitForm {

  val form = Form(
      tuple(
    		"identifier" -> nonEmptyText,
    		"name" -> nonEmptyText,
    		"publicationStatus" -> enum(defines.PublicationStatus)
      )
  ) 
}
