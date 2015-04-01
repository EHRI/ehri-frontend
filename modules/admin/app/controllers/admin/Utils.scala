package controllers.admin

import java.io.File

import auth.AccountManager
import controllers.base.AdminController
import models.Group
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._

import com.google.inject._
import play.api.mvc.Action
import backend.Backend
import play.api.libs.ws.WS
import backend.rest.RestDAO
import utils.PageParams
import backend.rest.cypher.CypherDAO

import scala.concurrent.Future.{successful => immediate}

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
    extends AdminController with RestDAO {

  override val staffOnly = false

  implicit val app = play.api.Play.current

  /**
   * Check the database is up by trying to load the admin account.
   */
  def checkDb = Action.async { implicit request =>
    // Not using the EntityDAO directly here to avoid caching
    // and logging
    WS.url("http://%s:%d/%s/group/admin".format(host, port, mount)).get().map { r =>
      r.json.validate[Group](Group.GroupResource.restReads).fold(
        _ => ServiceUnavailable("ko\nbad json"),
        _ => Ok("ok")
      )
    } recover {
      case err => ServiceUnavailable("ko\n" + err.getClass.getName)
    }
  }

  val movedItemsForm = Form(
    single("path-prefix" -> nonEmptyText.verifying("isPath", s => s.startsWith("/") && s.endsWith("/")))
  )

  def addMovedItems() = AdminAction { implicit request =>
    Ok(views.html.admin.movedItemsForm(movedItemsForm,
        controllers.admin.routes.Utils.addMovedItemsPost()))
  }

  def addMovedItemsPost() = AdminAction.async(parse.multipartFormData) { implicit request =>
    val boundForm: Form[String] = movedItemsForm.bindFromRequest
    boundForm.fold(
      errForm => immediate(Ok(views.html.admin.movedItemsForm(errForm,
          controllers.admin.routes.Utils.addMovedItemsPost()))),
      prefix =>
        request.body.file("csv").map { file =>
          val data = parseCsv(file.ref.file).map { case (from, to) =>
            s"$prefix$from" -> s"$prefix$to"
          }
          pageRelocator.addMoved(data).map { inserted =>
            Ok(views.html.admin.movedItemsAdded(data))
          }
        }.getOrElse {
          immediate(Ok(views.html.admin.movedItemsForm(
            boundForm.withError("csv", "No CSV file found"),
            controllers.admin.routes.Utils.addMovedItemsPost())))
        }
    )
  }

  private def parseCsv(file: File): Seq[(String, String)] = {
    import java.io.{InputStreamReader, FileInputStream}
    import au.com.bytecode.opencsv.CSVReader
    import scala.collection.JavaConverters._

    val csvReader: CSVReader = new CSVReader(
      new InputStreamReader(
        new FileInputStream(file), "UTF-8"), '\t')
    val all = csvReader.readAll()
    (for {
      arr <- all.asScala
    } yield arr.toList).toSeq.collect {
      case from :: to :: _ => from -> to
    }
  }

  /**
   * Check users in the accounts DB have profiles in
   * the graph DB, and vice versa.
   */
  def checkUserSync = Action.async { implicit request =>
    for {
      allAccounts <- accounts.findAll(PageParams.empty.withoutLimit)
      profileIds <- CypherDAO().get(
        """START n = node:entities("__ISA__:userProfile")
          |RETURN n.__ID__
        """.stripMargin, Map.empty)(CypherDAO.stringList)
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
