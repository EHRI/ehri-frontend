package controllers.admin

import controllers.base.{AuthController, ControllerHelpers}
import models.Group
import play.api.libs.concurrent.Execution.Implicits._

import com.google.inject._
import play.api.mvc.Action
import rest.{ApiUser, Backend}

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(implicit val globalConfig: global.GlobalConfig, backend: Backend) extends AuthController with ControllerHelpers {

  override val staffOnly = false

  /**
   * Check the database is up by trying to load the admin account.
   */
  val checkDb = Action { implicit request =>

    implicit val apiUser = new ApiUser

    Async {
      backend.query("group/admin", request.headers).map { r =>
        r.json.validate[Group](Group.Converter.restReads).fold(
          _ => ServiceUnavailable("ko\nbad json"),
          _ => Ok("ok")
        )
      } recover {
        case err => ServiceUnavailable("ko\n" + err.getClass.getName)
      }
    }
  }
}
