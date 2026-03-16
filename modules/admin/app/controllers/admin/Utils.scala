package controllers.admin

import controllers.AppComponents
import controllers.base.AdminController
import org.apache.pekko.stream.scaladsl.Sink
import play.api.libs.json._
import play.api.mvc._
import services.cypher.CypherService
import services.data.AuthenticatedUser
import utils.PageParams

import javax.inject._
import scala.concurrent.Future


/**
  * Controller for various monitoring functions and admin utilities.
  */
@Singleton
case class Utils @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService,
) extends AdminController {

  override val staffOnly = false

  /** Check the database is up by trying to load the admin account.
    */
  def checkServices: Action[AnyContent] = Action.async { implicit request =>
    val checkDbF = dataApi.withContext(AuthenticatedUser("admin")).status()
      .recover { case e => s"ko: ${e.getMessage}" }.map(s => s"ehri\t$s")
    val checkSearchF = searchEngine.status()
      .recover { case e => s"ko: ${e.getMessage}" }.map(s => s"solr\t$s")

    Future.sequence(Seq(checkDbF, checkSearchF)).map(_.mkString("\n")).map { s =>
      if (s.contains("ko")) ServiceUnavailable(s) else Ok(s)
    }
  }

  private case class CheckUser(id: String, active: Boolean, staff: Boolean)

  /** Check users in the accounts DB have profiles in
    * the graph DB, and vice versa.
    */
  def checkUserSync: Action[AnyContent] = Action.async { implicit request =>

    for {
      allAccounts <- accounts.findAll(PageParams.empty.withoutLimit)
      profiles <- cypher.rows(
          """MATCH (n:UserProfile)
            |RETURN n.__id, COALESCE(n.active, false), COALESCE(n.staff, false)""".stripMargin)
        .collect {
          case JsString(id) :: JsBoolean(active) :: JsBoolean(staff) :: _ => CheckUser(id, active, staff)
        }.runWith(Sink.seq).map(_.toSet)
      accounts = allAccounts.map(a => CheckUser(a.id, a.active, a.staff)).toSet
    } yield {
      val noProfile = accounts.map(_.id).diff(profiles.map(_.id))
      // Going nicely imperative here - sorry!
      var out = ""
      if (noProfile.nonEmpty) {
        out += "Users have account but no profile\n"
        noProfile.foreach { u =>
          out += s"  $u\n"
        }
      }

      Ok(out)
    }
  }
}
