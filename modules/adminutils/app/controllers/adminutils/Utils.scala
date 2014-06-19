package controllers.adminutils

import controllers.base.{AuthController, ControllerHelpers}
import models.{AccountDAO, Group}
import play.api.libs.concurrent.Execution.Implicits._

import com.google.inject._
import play.api.mvc.Action
import backend.{ApiUser, Backend}
import play.api.libs.ws.WS
import backend.rest.RestDAO

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO)
    extends AuthController with ControllerHelpers with RestDAO {

  override val staffOnly = false

  /**
   * Check the database is up by trying to load the admin account.
   */
  val checkDb = Action.async { implicit request =>
    // Not using the EntityDAO directly here to avoid caching
    // and logging
    WS.url("http://%s:%d/%s/group/admin".format(host, port, mount)).get().map { r =>
      r.json.validate[Group](Group.Converter.restReads).fold(
        _ => ServiceUnavailable("ko\nbad json"),
        _ => Ok("ok")
      )
    } recover {
      case err => ServiceUnavailable("ko\n" + err.getClass.getName)
    }
  }
}
