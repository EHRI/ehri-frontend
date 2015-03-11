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
  languages: Seq[String] = Nil,
  about: Option[String] = None,
  url: Option[String] = None,
  workUrl: Option[String] = None,
  title: Option[String] = None,
  institution: Option[String] = None,
  role: Option[String] = None,
  interests: Option[String] = None
)

object ProfileData {

  def fromUser(user: UserProfile): ProfileData = new ProfileData(
    user.model.name, user.model.location, user.model.languages,
    user.model.about, user.model.url, user.model.workUrl,
    user.model.title, user.model.institution, user.model.role,
    user.model.interests
  )

  import play.api.data.Forms._
  import play.api.data.Form
  import UserProfileF.{LOCATION => USERLOC, _}
  import utils.forms.isValidUrl

  implicit val writes: Writes[ProfileData] = Json.format[ProfileData]
  val form: Form[ProfileData] = Form(
    mapping(
      NAME -> nonEmptyText,
      USERLOC -> optional(text),
      LANGUAGES -> seq(nonEmptyText(minLength = 3, maxLength = 3)),
      ABOUT -> optional(text),
      URL -> optional(text.verifying(s => isValidUrl(s))),
      WORK_URL -> optional(text.verifying(s => isValidUrl(s))),
      TITLE -> optional(text),
      INSTITUTION -> optional(text),
      ROLE -> optional(text),
      INTERESTS -> optional(text)
    )(ProfileData.apply)(ProfileData.unapply)
  )
}

