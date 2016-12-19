package controllers.admin

import java.io.{ByteArrayInputStream, FileInputStream}
import java.nio.charset.StandardCharsets
import javax.inject._

import backend.AuthenticatedUser
import backend.rest.cypher.Cypher
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvParser
import controllers.Components
import controllers.base.AdminController
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.Action
import utils.{CsvHelpers, PageParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import java.io.InputStream
import java.net.URLEncoder

import backend.rest.Constants
import backend.rest.Constants.LOG_MESSAGE_HEADER_NAME
import defines.EntityType
import play.api.i18n.Messages
import utils.search.SearchIndexMediator

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(
  components: Components,
  searchIndexer: SearchIndexMediator,
  ws: WSClient,
  cypher: Cypher
) extends AdminController {

  private val logger = play.api.Logger(getClass)

  override val staffOnly = false

  private val dbBaseUrl = utils.serviceBaseUrl("ehridata", config)

  /**
   * Check the database is up by trying to load the admin account.
   */
  def checkServices = Action.async { implicit request =>
    val checkDbF = dataApi.withContext(AuthenticatedUser("admin")).status()
      .recover { case e => s"ko: ${e.getMessage}"}.map(s => s"ehri\t$s")
    val checkSearchF = searchEngine.config.status()
      .recover { case e => s"ko: ${e.getMessage}"}.map(s => s"solr\t$s")

    Future.sequence(Seq(checkDbF, checkSearchF)).map(_.mkString("\n")).map { s =>
      if (s.contains("ko")) ServiceUnavailable(s) else Ok(s)
    }
  }

  private val movedItemsForm = Form(
    single("path-prefix" -> nonEmptyText.verifying("isPath", s => s.startsWith("/") && s.endsWith("/")))
  )

  def addMovedItems() = AdminAction { implicit request =>
    Ok(views.html.admin.movedItemsForm(movedItemsForm,
        controllers.admin.routes.Utils.addMovedItemsPost()))
  }

  private val regenerateForm: Form[Seq[(String, String, Boolean)]] = Form(
    single("row" -> seq(tuple("from" -> nonEmptyText, "to" -> nonEmptyText, "active" -> boolean)))
  )

  def regenerateIds() = AdminAction.async { implicit request =>
    if (isAjax) {
      ws.url(s"$dbBaseUrl/tools/regenerate-ids-for-type/${EntityType.DocumentaryUnit}")
        .post("").map { r =>
        val items: Seq[(String,String)] = parseCsv(
          new ByteArrayInputStream(r.bodyAsBytes.toArray), ',').collect {
          case from :: to :: _ => from -> to
        }
        Ok(views.html.admin.regenerateIdsForm(regenerateForm
            .fill(items.map{case (f, t) => (f, t, true)}),
          controllers.admin.routes.Utils.regenerateIdsPost()))
      }
    } else immediate(Ok(views.html.admin.regenerateIds(regenerateForm,
      controllers.admin.routes.Utils.regenerateIdsPost())))
  }

  def regenerateIdsPost() = AdminAction.async { implicit request =>

    val boundForm: Form[Seq[(String, String, Boolean)]] = regenerateForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.regenerateIds(errForm,
        controllers.admin.routes.Utils.regenerateIdsPost()))),

      formItems => {
        val activeIds = formItems.collect{ case (f, t, true) => f}
        logger.info(s"Renaming: $activeIds")

        ws.url(s"$dbBaseUrl/tools/regenerate-ids")
          .withQueryString(activeIds.map(id => Constants.ID_PARAM -> id): _*)
          .withQueryString("commit" -> true.toString).post("").flatMap { r =>

          val items = parseCsv(new ByteArrayInputStream(r.bodyAsBytes.toArray), ',').collect {
            case from :: to :: _ => from -> to
          }
          // For each item we're renaming create 301s for the browse, search, and admin URLs
          val newUrls = items.flatMap { case (from, to) =>
            val portalBrowse = (controllers.portal.routes.DocumentaryUnits.browse(from).url,
              controllers.portal.routes.DocumentaryUnits.browse(to).url)
            val portalSearch = (controllers.portal.routes.DocumentaryUnits.search(from).url,
              controllers.portal.routes.DocumentaryUnits.search(to).url)
            val admin = (controllers.units.routes.DocumentaryUnits.get(from).url,
              controllers.units.routes.DocumentaryUnits.get(to).url)
            Seq(portalBrowse, portalSearch, admin)
          }

          for {
            // Delete the old IDs from the search engine...
            _ <- searchIndexer.handle.clearIds(items.map(_._1): _*)
            // Index the new ones...
            _ <- searchIndexer.handle.indexIds(items.map(_._2): _*)
            // Add the 301s to the DB...
            redirectCount <- components.pageRelocator.addMoved(newUrls)
          } yield Ok(views.html.admin.movedItemsAdded(newUrls))
        }
      }
    )
  }

  def addMovedItemsPost() = AdminAction.async(parse.multipartFormData) { implicit request =>
    def enc(s: String) = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name())
    def dec(s: String) = java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name())

    val boundForm: Form[String] = movedItemsForm.bindFromRequest
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.movedItemsForm(errForm,
          controllers.admin.routes.Utils.addMovedItemsPost()))),
      prefix =>
        request.body.file("tsv").map { file =>
          val data = parseCsv(new FileInputStream(file.ref.file)).collect {
            case from :: to :: _ => from -> to
          }.map { case (from, to) =>
            s"$prefix${enc(from)}" -> s"$prefix${enc(to)}"
          }
          components.pageRelocator.addMoved(data).map { inserted =>
            Ok(views.html.admin.movedItemsAdded(data.map(p => dec(p._1) -> dec(p._2))))
          }
        }.getOrElse {
          logger.error("Missing TSV for redirect upload...")
          immediate(Ok(views.html.admin.movedItemsForm(
            boundForm.withError("tsv", "No TSV file found"),
            controllers.admin.routes.Utils.addMovedItemsPost())))
        }
    )
  }

  import models.admin.FindReplaceTask

  private def msgHeader(msg: Option[String]): Seq[(String,String)] =
    msg
      .map(m => Seq(LOG_MESSAGE_HEADER_NAME -> URLEncoder.encode(m, StandardCharsets.UTF_8.name)))
      .getOrElse(Seq.empty)

  private def doFindReplace(task: FindReplaceTask, userId: String, commit: Boolean = false): Future[Seq[(String, String, String)]] =
    ws.url(s"$dbBaseUrl/tools/find-replace")
      .withHeaders(Constants.AUTH_HEADER_NAME -> userId)
      .withHeaders(msgHeader(task.log): _*)
      .withQueryString(
        FindReplaceTask.PARENT_TYPE -> task.parentType.toString,
        FindReplaceTask.SUB_TYPE -> task.subType.toString,
        FindReplaceTask.PROPERTY -> task.property,
        "commit" -> commit.toString)
      .post(Map(
        FindReplaceTask.FIND -> Seq(task.find),
        FindReplaceTask.REPLACE -> Seq(task.replace))).map { r =>
      parseCsv(new ByteArrayInputStream(r.bodyAsBytes.toArray), ',').collect {
        case pid :: cid :: textValue :: _ => (pid, cid, textValue)
      }
    }

  def findReplace = AdminAction.apply { implicit request =>
    Ok(views.html.admin.findReplace(FindReplaceTask.form, None,
      controllers.admin.routes.Utils.findReplacePost(),
      controllers.admin.routes.Utils.findReplacePost(commit = true)))
  }

  def findReplacePost(commit: Boolean = false) = AdminAction.async { implicit request =>
    val boundForm = FindReplaceTask.form.bindFromRequest()
    boundForm.fold(
      errForm => immediate(
        BadRequest(views.html.admin.findReplace(errForm, None,
          controllers.admin.routes.Utils.findReplacePost(),
          controllers.admin.routes.Utils.findReplacePost(commit = true)))
      ),
      fr => doFindReplace(fr, request.user.id, commit = commit).flatMap { found =>
        if (commit) {
          searchIndexer.handle.indexIds(found.map(_._1): _*).map { _ =>
            Redirect(controllers.admin.routes.Utils.findReplace())
              .flashing("success" -> Messages("admin.utils.findReplace.done", found.size))
          }
        }
        else immediate(Ok(views.html.admin.findReplace(boundForm, Some(found),
          controllers.admin.routes.Utils.findReplacePost(),
          controllers.admin.routes.Utils.findReplacePost(commit = true))))
      }
    )
  }

  private def parseCsv(ios: InputStream, sep: Char = '\t'): Seq[List[String]] = {
    import com.fasterxml.jackson.dataformat.csv.CsvSchema
    import scala.collection.JavaConverters._

    val schema = CsvSchema.builder().setColumnSeparator(sep).build()
    val all: MappingIterator[Array[String]] = CsvHelpers
      .mapper
      .enable(CsvParser.Feature.WRAP_AS_ARRAY)
      .readerFor(classOf[Array[String]])
      .`with`(schema)
      .readValues(ios)
    try {
      for {
        arr <- all.readAll().asScala
      } yield arr.toList
    } finally {
      all.close()
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
      profileIds <- cypher.get("MATCH (n:UserProfile) RETURN n.__id", Map.empty)(stringList)
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
