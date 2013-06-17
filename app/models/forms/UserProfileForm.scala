package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{UserProfileF, Entity}

/**
 * UserProfile model form.
 */
object UserProfileForm {

  import UserProfileF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> nonEmptyText,
      LOCATION -> optional(text),
      ABOUT -> optional(text),
      LANGUAGES -> optional(list(nonEmptyText))
    )(UserProfileF.apply)(UserProfileF.unapply)
  )
}
