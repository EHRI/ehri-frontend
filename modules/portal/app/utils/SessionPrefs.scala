package utils

import play.api.data.Form

/**
 * Helper class for encapsulating session-backed user
 * preferences.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SessionPrefs(
  showUserContent: Boolean = true,
  defaultLanguages: Option[Seq[String]] = None
)

object SessionPrefs {

  private val SHOW_USER_CONTENT = "showUserContent"
  private val DEFAULT_LANGUAGES = "defaultLanguages"

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val reads: Reads[SessionPrefs] = (
    (__ \ SHOW_USER_CONTENT).readNullable[Boolean].map(_.getOrElse(true)) and
    (__ \ DEFAULT_LANGUAGES).readNullable[Seq[String]]
  )(SessionPrefs.apply _)

  implicit val writes: Writes[SessionPrefs] = Json.writes[SessionPrefs]
  implicit val fmt: Format[SessionPrefs] = Format(reads, writes)

  /**
   * Update the session preferences from form values in the
   * current request, returning the new object.
   */
  def updateForm(current: SessionPrefs): Form[SessionPrefs] = {
    import play.api.data.Forms._

    Form(
      mapping(
        SHOW_USER_CONTENT -> default(boolean, current.showUserContent),
        DEFAULT_LANGUAGES -> default(optional(seq(nonEmptyText(minLength = 3))), current.defaultLanguages)
      )(SessionPrefs.apply)(SessionPrefs.unapply)
    )
  }
}
