package controllers.admin

import java.io.File

import auth.AccountManager
import controllers.base.AdminController
import defines.EntityType
import models.Group
import models.base.Accessor
import play.api.cache.CacheApi
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._

import javax.inject._
import play.api.libs.json._
import play.api.mvc.Action
import backend.Backend
import play.api.libs.ws.WSClient
import utils.{CsvHelpers, MovedPageLookup, PageParams}
import backend.rest.cypher.Cypher
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher,
  ws: WSClient
) extends AdminController {

  override val staffOnly = false

  private val baseUrl = utils.serviceBaseUrl("ehridata", app.configuration)

  /**
   * Check the database is up by trying to load the admin account.
   */
  def checkDb = Action.async { implicit request =>
    // Not using the EntityDAO directly here to avoid caching
    // and logging
    ws.url(s"$baseUrl/${EntityType.Group}/${Accessor.ADMIN_GROUP_NAME}").get().map { r =>
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
    import java.io.FileInputStream
    import scala.collection.JavaConverters._
    import com.fasterxml.jackson.dataformat.csv.CsvSchema

    val schema = CsvSchema.builder().setColumnSeparator('\t').build()
    val all: java.util.List[Array[String]] = CsvHelpers.mapper
      .reader(schema).readValue(new FileInputStream(file))
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
    val stringList: Reads[Seq[String]] =
      (__ \ "data").read[Seq[Seq[String]]].map(_.flatMap(_.headOption))

    for {
      allAccounts <- accounts.findAll(PageParams.empty.withoutLimit)
      profileIds <- cypher.get("MATCH (n:userProfile) RETURN n.__id", Map.empty)(stringList)
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
