package controllers.admin

import java.io.File
import java.nio.charset.StandardCharsets

import auth.AccountManager
import controllers.base.AdminController
import play.api.cache.CacheApi
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import javax.inject._

import auth.handler.AuthHandler
import play.api.libs.json._
import play.api.mvc.Action
import backend.{AuthenticatedUser, DataApi}
import play.api.libs.ws.WSClient
import utils.{CsvHelpers, MovedPageLookup, PageParams}
import backend.rest.cypher.Cypher
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvParser
import utils.search.SearchEngine
import views.MarkdownRenderer

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}

/**
 * Controller for various monitoring functions.
 */
@Singleton
case class Utils @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  searchEngine: SearchEngine,
  dataApi: DataApi,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher,
  ws: WSClient
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

  val movedItemsForm = Form(
    single("path-prefix" -> nonEmptyText.verifying("isPath", s => s.startsWith("/") && s.endsWith("/")))
  )

  def addMovedItems() = AdminAction { implicit request =>
    Ok(views.html.admin.movedItemsForm(movedItemsForm,
        controllers.admin.routes.Utils.addMovedItemsPost()))
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
          val data = parseCsv(file.ref.file).map { case (from, to) =>
            s"$prefix${enc(from)}" -> s"$prefix${enc(to)}"
          }
          pageRelocator.addMoved(data).map { inserted =>
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

  private def parseCsv(file: File): Seq[(String, String)] = {
    import java.io.FileInputStream
    import scala.collection.JavaConverters._
    import com.fasterxml.jackson.dataformat.csv.CsvSchema

    val schema = CsvSchema.builder().setColumnSeparator('\t').build()
    val all: MappingIterator[Array[String]] = CsvHelpers
      .mapper
      .enable(CsvParser.Feature.WRAP_AS_ARRAY)
      .readerFor(classOf[Array[String]])
      .`with`(schema)
      .readValues(new FileInputStream(file))
    try {
      (for {
        arr <- all.readAll().asScala
      } yield arr.toList).collect {
        case from :: to :: _ => from -> to
      }
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
