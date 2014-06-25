package controllers.core

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{AuthController, AuthConfigImpl}
import models.base.AnyModel
import models.json.RestReadable
import play.api.mvc._
import jp.t2v.lab.play2.auth.AsyncAuth
import play.api.libs.json.Json
import defines.EntityType
import com.google.inject._
import play.api.http.{MimeTypes, ContentTypes}
import backend.Backend
import backend.rest.SearchDAO
import models.AccountDAO
import play.api.Routes

case class Application @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AsyncAuth with AuthConfigImpl with AuthController {

  override val staffOnly = false
  override val verifiedOnly = false

  /**
   * Handle trailing slashes with a permanent redirect.
   */
  def untrail(path: String) = Action { request =>
    val query = if (request.rawQueryString != "") "?" + request.rawQueryString else ""
    MovedPermanently("/" + path + query)
  }

  def jsRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        controllers.core.routes.javascript.SearchFilter.filter
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def localeData(lang: String) = Action { request =>
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

