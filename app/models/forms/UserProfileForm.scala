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
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      LOCATION -> optional(nonEmptyText),
      ABOUT -> optional(nonEmptyText),
      LANGUAGES -> optional(list(nonEmptyText))
    )(UserProfileF.apply)(UserProfileF.unapply)
  )
}
