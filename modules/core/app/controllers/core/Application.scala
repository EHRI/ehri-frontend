package controllers.core

import play.api.mvc._
import play.api.http.{MimeTypes, ContentTypes}
import play.api.Routes
import play.api.cache.Cached
import play.api.Play.current

object Application extends Controller {

  /**
   * Provide functionality for changing the current locale.
   *
   * This is borrowed from:
   * https://github.com/julienrf/chooze/blob/master/app/controllers/CookieLang.scala
   */
  private val LANG = "lang"

  def changeLocale(lang: String) = Action { implicit request =>
    val referrer = request.headers.get(REFERER).getOrElse("/")
    Redirect(referrer).withCookies(Cookie(LANG, lang))
  }

  /**
   * Handle trailing slashes with a permanent redirect.
   */
  def untrail(path: String) = Action { request =>
    val query = if (request.rawQueryString != "") "?" + request.rawQueryString else ""
    MovedPermanently("/" + path + query)
  }

  def localeData(lang: String) = Cached.status(_ => "pages:localeData", OK, 3600) {
    Action { request =>
      implicit val locale = play.api.i18n.Lang(lang)

      val js =
        """
          |var __languageData = {
          |  %s
          |};
          |
          |var __countryData = {
          |  %s
          |};
          |
          |var LocaleData = {
          |  languageCodeToName: function(code) {
          |    return __languageData[code] || code;
          |  },
          |  countryCodeToName: function(code) {
          |    return __countryData[code] || code;
          |  },
          |}
        """.stripMargin.format(
          utils.i18n.languagePairList.map{ case (code, name) =>
            code + ": \"" + name + "\""
          }.mkString(",\n  "),
          utils.i18n.countryPairList.map{ case (code, name) =>
            code.toLowerCase + ": \"" + name + "\""
          }.mkString(",\n  ")
        )

      Ok(js).as(ContentTypes.JAVASCRIPT)
    }
  }
}

