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
import play.api.http.ContentTypes
import java.util.Locale
import backend.Backend
import backend.rest.SearchDAO
import models.AccountDAO

case class Application @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AsyncAuth with AuthConfigImpl with AuthController {

  implicit val rd: RestReadable[AnyModel] = AnyModel.Converter

  private object searchDao extends SearchDAO

  /**
   * Action for redirecting to any item page, given a raw id.
   * TODO: Ultimately implement this in a better way, not
   * requiring two DB hits (including the redirect...)
   * @param id
   * @return
   */
  def get(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    implicit val rd: RestReadable[AnyModel] = AnyModel.Converter
    searchDao.list(List(id)).map {
      case Nil => NotFound(views.html.errors.itemNotFound())
      case mm :: _ => globalConfig.routeRegistry.optionalUrlFor(mm.isA, mm.id)
          .map(Redirect) getOrElse NotFound(views.html.errors.itemNotFound())
    }
  }

  def getGeneric(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    searchDao.get[AnyModel](id).map { item =>
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
      .map(Redirect)
      .getOrElse(NotFound(views.html.errors.itemNotFound()))
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

