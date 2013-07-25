package controllers.core

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{LoginHandler, AuthController, Authorizer}
import models.base.AnyModel
import models.json.RestReadable
import global.GlobalConfig
import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.{LoginLogout, Auth}
import play.api.Play._
import defines.EntityType
import utils.search.Dispatcher
import com.google.inject._

class Application @Inject()(val globalConfig: GlobalConfig) extends Controller with Auth with LoginLogout with Authorizer with AuthController {

  lazy val loginHandler: LoginHandler = globalConfig.loginHandler

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout

  def index = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      Ok(views.html.index("Your new application is ready."))
    }
  }

  /**
   * Look up the 'show' page of a generic item id
   * @param id
   */
  def genericShow(id: String) = Action {
    NotImplemented
  }


  /**
   * Action for redirecting to any item page, given a raw id.
   * TODO: Ultimately implement this in a better way, not
   * requiring two DB hits (including the redirect...)
   * @param id
   * @return
   */
  def get(id: String) = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      AsyncRest {
        implicit val rd: RestReadable[AnyModel] = AnyModel.Converter
        rest.SearchDAO(userOpt).list(List(id)).map { listOrErr =>
          listOrErr.right.map{ list =>
            list match {
              case Nil => NotFound(views.html.errors.itemNotFound())
              case mm :: _ =>
                views.Helpers.optionalUrlFor(mm.isA, mm.id).map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
            }
          }
        }
      }
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
    Secured {
      views.Helpers.optionalUrlFor(EntityType.withName(`type`), id).map(Redirect(_)) getOrElse NotFound(views.html.errors.itemNotFound())
    }
  }
}

