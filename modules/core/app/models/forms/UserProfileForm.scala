package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{UserProfileF, Entity}
import defines.EntityType
import utils.forms._

/**
 * UserProfile model form.
 */
object UserProfileForm {

  import UserProfileF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.UserProfile),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> nonEmptyText,
      LOCATION -> optional(text),
      ABOUT -> optional(text),
      LANGUAGES -> list(nonEmptyText),
      IMAGE_URL -> optional(nonEmptyText.verifying(s => isValidOpenIDUrl(s))))
    (UserProfileF.apply)(UserProfileF.unapply)
  )
}
