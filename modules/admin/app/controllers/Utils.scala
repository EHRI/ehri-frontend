package controllers.admin

import controllers.base.{AuthController, ControllerHelpers}
import models.Group
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import defines.EntityType

import com.google.inject._
import play.api.mvc.Action
import rest.EntityDAO
import utils.search.{Indexer, Dispatcher}
import play.api.libs.ws.WS


/**
 * Controller for various monitoring functions.
 */
@Singleton
class Utils @Inject()(implicit val globalConfig: global.GlobalConfig,
                      searchDispatcher: Dispatcher,
                      searchIndexer: Indexer) extends AuthController with ControllerHelpers {

  override val staffOnly = false

  /**
   * Check the database is up by trying to load the admin account.
   */
  val checkDb = Action.async { implicit request =>
    // Not using the EntityDAO directly here to avoid caching
    // TODO: Make caching configurable...
    WS.url(EntityDAO(EntityType.Group).requestUrl + "/admin").get.map { r =>
      r.json.validate[Group](Group.Converter.restReads).fold(
        _ => ServiceUnavailable("ko\nbad json"),
        _ => Ok("ok")
      )
    } recover {
      case err => ServiceUnavailable("ko\n" + err.getClass.getName)
    }
  }
}
