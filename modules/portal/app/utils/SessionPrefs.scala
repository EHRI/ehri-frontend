package utils

import play.api.mvc.{SimpleResult, RequestHeader}

/**
 * Helper class for loading/saving session-stored user preferences
 * from request and saving in response.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SessionPrefs(
  showUserContent: Boolean = true,
  defaultLanguages: Option[Seq[String]] = None
)

object SessionPrefs {

  val STORE_KEY = "userPrefs"
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
  
  def defaults = new SessionPrefs

  /**
   * Load preferences, ensuring we never error.
   */
  def load(implicit request: RequestHeader): SessionPrefs = try {
    (for {
      prefString <- request.session.get("userPrefs")
      prefs <- Json.toJson(prefString).validate(reads).asOpt
    } yield prefs).getOrElse(defaults)
  } catch {
    // Ensure that we *never* throw an exception on missing
    // or corrupt preferences...
    case _: Throwable => defaults 
  }

  /**
   * Implicit extension method on RequestHeader to allow preferences
   * class to be straightforwardly loaded.
   */
  implicit class RequestHeaderOps(request: RequestHeader) {
    def preferences: SessionPrefs = SessionPrefs.load(request)
  }

  /**
   * Implicit extension method class that can be imported to provide
   * a `withPreferences` method to the SimpleResult class.
   */
  implicit class SimpleResultOps(result: SimpleResult) {
    def withPreferences(preferences: SessionPrefs)(implicit request: RequestHeader): SimpleResult = {
      result.withSession(
        request.session + (STORE_KEY -> Json.stringify(Json.toJson(preferences))))
    }
  }
}
