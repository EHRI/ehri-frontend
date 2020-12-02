package controllers.admin

import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import javax.inject._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.cypher.CypherService
import services.data.AuthenticatedUser
import services.ingest.EadValidator
import services.search.SearchIndexMediator
import services.storage.FileStorage
import utils.PageParams

import scala.concurrent.Future


/**
  * Controller for various monitoring functions and admin utilities.
  */
@Singleton
case class Utils @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient,
  eadValidator: EadValidator,
  cypher: CypherService,
  @Named("dam") storage: FileStorage,
)(implicit mat: Materializer) extends AdminController {

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

  /** Check users in the accounts DB have profiles in
    * the graph DB, and vice versa.
    */
  def checkUserSync: Action[AnyContent] = Action.async { implicit request =>

    for {
      allAccounts <- accounts.findAll(PageParams.empty.withoutLimit)
      profileIds <- cypher.get("MATCH (n:UserProfile) RETURN n.__id").map {
        res => res.data.collect { case JsString(id) :: _ => id }.flatten
      }
      accountIds = allAccounts.map(_.id)
    } yield {
      val noProfile = accountIds.diff(profileIds)
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
