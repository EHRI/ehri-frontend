package controllers.adminutils

import controllers.base.{AuthController, ControllerHelpers}
import models.{Account, AccountDAO, Group}
import play.api.libs.concurrent.Execution.Implicits._

import com.google.inject._
import play.api.mvc.Action
import backend.Backend
import play.api.libs.ws.WS
import backend.rest.RestDAO
import utils.PageParams
import backend.rest.cypher.CypherDAO

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO)
    extends AuthController with ControllerHelpers with RestDAO {

  override val staffOnly = false

  implicit val app = play.api.Play.current

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

  /**
   * Check users in the accounts DB have profiles in
   * the graph DB, and vice versa.
   */
  val checkUserSync = Action.async { implicit request =>
    val accounts: Seq[Account] = userDAO.findAll(PageParams.empty.withoutLimit)
    for {
      profileIds <- CypherDAO().get(
        """START n = node:entities("__ISA__:userProfile")
          |RETURN n.__ID__
        """.stripMargin, Map.empty, CypherDAO.stringList)
      accountIds = accounts.map(_.id)
    } yield {
      val noProfile = accountIds.diff(profileIds)
      // Going nicely imperative here - sorry!
      var out = ""
      if (!noProfile.isEmpty) {
        out += "Users have account but no profile\n"
        noProfile.foreach { u =>
          out += s"  $u\n"
        }
      }
      Ok(out)
    }
  }
}
