package models

import play.api.libs.json.{Json, Writes}

/**
 * Class representing the parts of a users profile editable
 * via the UI. This is basically everything, with the exception
 * of their (automatically-assigned) identifier, and - currently -
 * their image (which is fetched automatically via OAuth2.)
 */
case class ProfileData(
  name: String,
  location: Option[String] = None,
  languages: List[String] = Nil,
  about: Option[String] = None
)

object ProfileData {

  def fromUser(user: UserProfile): ProfileData = new ProfileData(
    user.model.name, user.model.location, user.model.languages,
    user.model.about
  )

  import play.api.data.Forms._
  import play.api.data.Form
  import UserProfileF.{LOCATION => USERLOC, _}

  implicit val writes: Writes[ProfileData] = Json.format[ProfileData]
  val form: Form[ProfileData] = Form(
    mapping(
      NAME -> nonEmptyText,
      USERLOC -> optional(text),
      LANGUAGES -> list(nonEmptyText(minLength = 3, maxLength = 3)),
      ABOUT -> optional(text)
    )(ProfileData.apply)(ProfileData.unapply)
  )
}

