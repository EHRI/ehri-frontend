package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.AuthController
import play.api.mvc.Controller
import controllers.base.ControllerHelpers

import com.google.inject._
import backend.Backend
import models.AccountDAO
import defines.EntityType
import models.json.RestReadable
import models.base.AnyModel
import backend.rest.SearchDAO

case class Admin @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Controller with AuthController with ControllerHelpers {

  implicit val rd: RestReadable[AnyModel] = AnyModel.Converter

  private object searchDao extends SearchDAO

  /**
   * Action for redirecting to any item page, given a raw id.
   */
  def get(id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    implicit val rd: RestReadable[AnyModel] = AnyModel.Converter
    searchDao.list(List(id)).map {
      case Nil => NotFound(views.html.errors.itemNotFound())
      case mm :: _ => globalConfig.routeRegistry.optionalUrlFor(mm.isA, mm.id)
        .map(Redirect) getOrElse NotFound(views.html.errors.itemNotFound())
    }
  }

  /**
   * Action for redirecting to any item page, given a raw id.
   */
  def getType(`type`: String, id: String) = userProfileAction { implicit userOpt => implicit request =>
    globalConfig.routeRegistry.optionalUrlFor(EntityType.withName(`type`), id)
      .map(Redirect)
      .getOrElse(NotFound(views.html.errors.itemNotFound()))
  }
}