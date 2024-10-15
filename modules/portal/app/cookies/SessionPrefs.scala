package cookies

import play.api.data.Form

/**
 * Helper class for encapsulating session-backed user
 * preferences.
 */
case class SessionPrefs(
  showUserContent: Boolean = true,
  defaultLanguages: Seq[String] = Nil,
  recentItems: Seq[String] = Nil
) {
  def withRecentItem(id: String): SessionPrefs =
    copy(recentItems = (id +: recentItems).distinct.take(10))
}

object SessionPrefs {

  import models.json.JsPathExtensions

  val SHOW_USER_CONTENT = "showUserContent"
  val DEFAULT_LANGUAGES = "defaultLanguages"
  val RECENT_ITEMS = "recentItems"

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val _reads: Reads[SessionPrefs] = (
    (__ \ SHOW_USER_CONTENT).readNullable[Boolean].map(_.getOrElse(true)) and
    (__ \ DEFAULT_LANGUAGES).readSeqOrEmpty[String] and
    (__ \ RECENT_ITEMS).readSeqOrEmpty[String]
  )(SessionPrefs.apply _)

  implicit val _writes: Writes[SessionPrefs] = (
    (__ \ SHOW_USER_CONTENT).write[Boolean] and
    (__ \ DEFAULT_LANGUAGES).writeSeqOrEmpty[String] and
    (__ \ RECENT_ITEMS).writeSeqOrEmpty[String]
  )(unlift(SessionPrefs.unapply))

  implicit val fmt: Format[SessionPrefs] = Format(_reads, _writes)

  /**
   * Update the session preferences from form values in the
   * current request, returning the new object.
   */
  def updateForm(current: SessionPrefs): Form[SessionPrefs] = {
    import play.api.data.Forms._

    Form(
      mapping(
        SHOW_USER_CONTENT -> default(boolean, current.showUserContent),
        DEFAULT_LANGUAGES -> default(seq(nonEmptyText(minLength = 3)), current.defaultLanguages),
        RECENT_ITEMS -> default(seq(nonEmptyText), current.recentItems)
      )(SessionPrefs.apply)(SessionPrefs.unapply)
    )
  }
}
