package controllers.core

import _root_.models.{AccountDAO, Account}
import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{LoginHandler, AuthController, Authorizer}
import models.base.AnyModel
import models.json.RestReadable
import global.GlobalConfig
import play.api._
import play.api.mvc._
import jp.t2v.lab.play2.auth.{AsyncAuth, LoginLogout, Auth}
import play.api.Play._
import play.api.libs.json.Json
import defines.EntityType
import utils.search.Dispatcher
import com.google.inject._
import play.api.http.ContentTypes
import java.util.Locale
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages

class Application @Inject()(implicit val globalConfig: GlobalConfig, val backend: rest.Backend) extends Controller with AsyncAuth with Authorizer with AuthController {

  implicit val rd: RestReadable[AnyModel] = AnyModel.Converter

  /**
   * Action for redirecting to any item page, given a raw id.
   * TODO: Ultimately implement this in a better way, not
   * requiring two DB hits (including the redirect...)
   * @param id
   * @return
   */
  def get(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    implicit val rd: RestReadable[AnyModel] = AnyModel.Converter
    rest.SearchDAO().list(List(id)).map { list =>
      list match {
        case Nil => NotFound(views.html.errors.itemNotFound())
        case mm :: _ =>
          globalConfig.routeRegistry.optionalUrlFor(mm.isA, mm.id)
            .map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
      }
    }
  }

  def getGeneric(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    rest.SearchDAO().get[AnyModel](id).map { item =>
      Ok(Json.toJson(item)(AnyModel.Converter.clientFormat))
    }
  }

  /**
   * Action for redirecting to any item page, given a raw id.
   * TODO: Ultimately implement this in a better way, not
   * requiring two DB hits (including the redirect...)
   * @param id
   * @return
   */
  def getType(`type`: String, id: String) = userProfileAction { implicit userOpt => implicit request =>
    globalConfig.routeRegistry.optionalUrlFor(EntityType.withName(`type`), id)
      .map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
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
        Locale.getISOLanguages.map{
          l => l + ": \"" + views.Helpers.languageCodeToName(l) + "\""
        }.mkString(",\n  "),
        Locale.getISOCountries.map{ cn =>
          cn.toLowerCase + ": \"" + views.Helpers.countryCodeToName(cn) + "\""
        }.mkString(",\n  ")
      )

    Ok(js).as(ContentTypes.JAVASCRIPT)
  }
}

