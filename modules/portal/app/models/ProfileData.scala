package models

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
) {

  def toUser(user: UserProfileF): UserProfileF = user.copy(
      name = name,
      location = location,
      languages = languages,
      about = about,
      url = url,
      workUrl = workUrl,
      title = title,
      institution = institution,
      role = role,
      interests = interests
    )
}

object ProfileData {

  def fromUser(user: UserProfileF): ProfileData = new ProfileData(
    user.name,
    user.location,
    user.languages,
    user.about,
    user.url,
    user.workUrl,
    user.title,
    user.institution,
    user.role,
    user.interests
  )

  import play.api.data.Forms._
  import play.api.data.Form
  import models.UserProfileF.{LOCATION => USERLOC, _}

  val form: Form[ProfileData] = Form(
    mapping(
      NAME -> nonEmptyText,
      USERLOC -> optional(text),
      LANGUAGES -> seq(nonEmptyText(minLength = 3, maxLength = 3)),
      ABOUT -> optional(text),
      URL -> optional(text.verifying(s => forms.isValidUrl(s))),
      WORK_URL -> optional(text.verifying(s => forms.isValidUrl(s))),
      TITLE -> optional(text),
      INSTITUTION -> optional(text),
      ROLE -> optional(text),
      INTERESTS -> optional(text)
    )(ProfileData.apply)(ProfileData.unapply)
  )
}

