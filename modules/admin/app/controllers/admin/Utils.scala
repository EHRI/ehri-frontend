package controllers.admin

import java.io.{FileInputStream, InputStream}
import java.nio.charset.StandardCharsets
import javax.inject._

import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvParser
import controllers.AppComponents
import controllers.base.AdminController
import defines.ContentTypes
import models.admin.BatchDeleteTask
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents, MultipartFormData}
import services.cypher.Cypher
import services.data.{AuthenticatedUser, InputDataError}
import services.search.SearchIndexMediator
import utils.{CsvHelpers, EnumUtils, PageParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


/**
  * Controller for various monitoring functions and admin utilities.
  */
@Singleton
case class Utils @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient,
  cypher: Cypher
) extends AdminController {

  override val staffOnly = false
  private def logger = play.api.Logger(classOf[Utils])

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


  private val pathPrefixField = nonEmptyText.verifying("isPath",
    s => s.split(',').forall(_.startsWith("/") && s.endsWith("/")))

  private val urlMapForm = Form(
    single("path-prefix" -> pathPrefixField)
  )

  def addMovedItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.tools.movedItemsForm(urlMapForm,
      controllers.admin.routes.Utils.addMovedItemsPost()))
  }

  def addMovedItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.tools.movedItemsForm(errForm,
          controllers.admin.routes.Utils.addMovedItemsPost()))),
        prefix => request.body.file("csv").map { file =>
          updateFromCsv(new FileInputStream(file.ref.path.toFile), prefix)
            .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
        }.getOrElse {
          immediate(Ok(views.html.admin.tools.movedItemsForm(
            boundForm.withError("csv", "No CSV file found"),
            controllers.admin.routes.Utils.addMovedItemsPost())))
        }
      )
    }

  def renameItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.tools.renameItemsForm(urlMapForm,
      controllers.admin.routes.Utils.renameItemsPost()))
  }

  def renameItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.tools
          .renameItemsForm(errForm, controllers.admin.routes.Utils.renameItemsPost()))),
        prefix => request.body.file("csv").filter(_.filename.nonEmpty).map { file =>
          val todo = parseCsv(new FileInputStream(file.ref.path.toFile))
            .collect { case from :: to :: _ => from -> to }
          userDataApi.rename(todo).flatMap { items =>
            updateFromCsv(items, prefix)
              .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
          } recover {
            case e: InputDataError =>
              BadRequest(views.html.admin.tools.renameItemsForm(
                boundForm.withGlobalError(e.details),
                controllers.admin.routes.Utils.renameItemsPost()))
          }
        }.getOrElse {
          immediate(BadRequest(views.html.admin.tools.renameItemsForm(
            boundForm.withGlobalError("No CSV file found"),
            controllers.admin.routes.Utils.renameItemsPost())))
        }
      )
    }

  def reparentItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.tools.reparentItemsForm(urlMapForm,
      controllers.admin.routes.Utils.reparentItemsPost()))
  }

  def reparentItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.tools
          .reparentItemsForm(errForm, controllers.admin.routes.Utils.reparentItemsPost()))),
        prefix => request.body.file("csv").filter(_.filename.nonEmpty).map { file =>
          val todo = parseCsv(new FileInputStream(file.ref.path.toFile))
            .collect { case id :: parent :: _ => id -> parent }
          userDataApi.reparent(todo, commit = true).flatMap { items =>
            updateFromCsv(items, prefix)
              .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
          } recover {
            case e: InputDataError =>
              BadRequest(views.html.admin.tools.reparentItemsForm(
                boundForm.withGlobalError(e.details),
                controllers.admin.routes.Utils.reparentItemsPost()))
          }
        }.getOrElse {
          immediate(BadRequest(views.html.admin.tools.reparentItemsForm(
            boundForm.withGlobalError("No CSV file found"),
            controllers.admin.routes.Utils.reparentItemsPost())))
        }
      )
    }

  private val regenerateForm: Form[(Option[ContentTypes.Value], Option[String], Boolean)] = Form(
    tuple(
      "type" -> optional(EnumUtils.enumMapping(ContentTypes)),
      "scope" -> optional(nonEmptyText).transform (_.flatMap {
          case "" => Option.empty
          case s => Some(s)
        }, identity[Option[String]]),
      "tolerant" -> boolean
    ).verifying("Choose one option OR the other", t => !(t._1.isDefined && t._2.isDefined))
  )

  def regenerateIds(): Action[AnyContent] = AdminAction.apply { implicit request =>
    val form = regenerateForm.bindFromRequest
    form.fold(
      err => BadRequest(views.html.admin.tools.regenerate(err, controllers.admin.routes.Utils.regenerateIds())), {
        case (Some(et), None, t) =>
          Redirect(controllers.admin.routes.Utils.regenerateIdsForType(et, t))
        case (None, Some(id), t) =>
          Redirect(controllers.admin.routes.Utils.regenerateIdsForScope(id, t))
        case d@(Some(_), Some(_), t) =>
          Ok(views.html.admin.tools.regenerate(
            regenerateForm.fill(d)
              .withGlobalError(Messages("admin.utils.regenerateIds.chooseOne")),
            controllers.admin.routes.Utils.regenerateIds()))
        case _ => Ok(views.html.admin.tools.regenerate(regenerateForm,
          controllers.admin.routes.Utils.regenerateIds()))
      }
    )
  }

  private val regenerateIdsForm: Form[(String, Seq[(String, String, Boolean)])] = Form(
    tuple(
      "path-prefix" -> pathPrefixField,
      "items" -> seq(tuple("from" -> nonEmptyText, "to" -> nonEmptyText, "active" -> boolean))
    )
  )

  def regenerateIdsForType(ct: defines.ContentTypes.Value, tolerant: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    if (isAjax) {
      userDataApi.regenerateIdsForType(ct, tolerant).map { items =>
        Ok(views.html.admin.tools.regenerateIdsForm(regenerateIdsForm
          .fill(value = ("", items.map { case (f, t) => (f, t, true) })),
          controllers.admin.routes.Utils.regenerateIdsPost(tolerant)))
      } recover {
        case e: InputDataError =>
          Ok(views.html.admin.tools.regenerateForm(regenerateForm
              .fill((Some(ct), None, tolerant))
              .withGlobalError(e.details),
            controllers.admin.routes.Utils.regenerateIds()))
      }
    } else immediate(Ok(views.html.admin.tools.regenerateIds(regenerateIdsForm,
      controllers.admin.routes.Utils.regenerateIdsForType(ct, tolerant))))
  }

  def regenerateIdsForScope(id: String, tolerant: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    if (isAjax) {
      userDataApi.regenerateIdsForScope(id, tolerant).map { items =>
        Ok(views.html.admin.tools.regenerateIdsForm(regenerateIdsForm
          .fill(value = ("", items.map { case (f, t) => (f, t, true)})),
          controllers.admin.routes.Utils.regenerateIdsPost(tolerant)))
      }
    } else immediate(Ok(views.html.admin.tools.regenerateIds(regenerateIdsForm,
      controllers.admin.routes.Utils.regenerateIdsForScope(id))))
  }

  private val parser = parsers.anyContent(maxLength = Some(5 * 1024 * 1024L))

  def regenerateIdsPost(tolerant: Boolean): Action[AnyContent] = AdminAction.async(parser) { implicit request =>
    val boundForm: Form[(String, Seq[(String, String, Boolean)])] = regenerateIdsForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.tools.regenerateIds(errForm,
        controllers.admin.routes.Utils.regenerateIdsPost(tolerant)))), {
      case (prefix, items) =>
        val activeIds = items.collect { case (f, _, true) => f }
        logger.info(s"Renaming: $activeIds")
        userDataApi.regenerateIds(activeIds, tolerant, commit = true).flatMap { items =>
          updateFromCsv(items, prefix)
            .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
        }
      }
    )
  }

  import models.admin.FindReplaceTask

  def findReplace: Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.tools.findReplace(FindReplaceTask.form, None,
      controllers.admin.routes.Utils.findReplacePost(),
      controllers.admin.routes.Utils.findReplacePost(commit = true)))
  }

  private val indexer = searchIndexer.handle

  def findReplacePost(commit: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    val boundForm = FindReplaceTask.form.bindFromRequest()
    boundForm.fold(
      errForm => immediate(
        BadRequest(views.html.admin.tools.findReplace(errForm, None,
          controllers.admin.routes.Utils.findReplacePost(),
          controllers.admin.routes.Utils.findReplacePost(commit = true)))
      ),
      task => userDataApi.findReplace(
          task.parentType, task.subType, task.property, task.find,
          task.replace, commit, task.log).flatMap { found =>
        if (commit) {
          indexer.indexIds(found.map(_._1): _*).map { _ =>
            Redirect(controllers.admin.routes.Utils.findReplace())
              .flashing("success" -> Messages("admin.utils.findReplace.done", found.size))
          }
        }
        else immediate(Ok(views.html.admin.tools.findReplace(boundForm, Some(found),
          controllers.admin.routes.Utils.findReplacePost(),
          controllers.admin.routes.Utils.findReplacePost(commit = true))))
      }
    )
  }

  def batchDelete: Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.tools.batchDelete(BatchDeleteTask.form,
      controllers.admin.routes.Utils.batchDeletePost()))
  }

  def batchDeletePost: Action[AnyContent] = AdminAction.async { implicit request =>
    val boundForm = BatchDeleteTask.form.bindFromRequest
    boundForm.fold(
      err => immediate(BadRequest(views.html.admin.tools.batchDelete(err,
        controllers.admin.routes.Utils.batchDeletePost()))),
      data => userDataApi.batchDelete(
        data.ids, data.scope, data.log, version = data.version, commit = data.commit
      ).map { deleted =>
        Redirect(controllers.admin.routes.Utils.batchDelete())
          .flashing("success" -> Messages("admin.utils.batchDelete.done", deleted))
      } recover {
        case e: InputDataError =>
          BadRequest(views.html.admin.tools.batchDelete(boundForm.withGlobalError(e.details),
            controllers.admin.routes.Utils.batchDeletePost()))
      }
    )
  }

  private def remapUrlsFromPrefixes(items: Seq[(String, String)], prefixes: String): Seq[(String, String)] = {
    def enc(s: String) = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name())

    items.flatMap { case (from, to) =>
      prefixes.split(',').map(p => s"$p${enc(from)}" -> s"$p${enc(to)}")
    }
  }

  private def updateMovedItems(items: Seq[(String, String)], newUrls: Seq[(String, String)]): Future[Int] = {
    for {
    // Delete the old IDs from the search engine...
      _ <- indexer.clearIds(items.map(_._1): _*)
      // Index the new ones....
      _ <- indexer.indexIds(items.map(_._2): _*)
      // Add the 301s to the DB...
      redirectCount <- appComponents.pageRelocator.addMoved(newUrls)
    } yield redirectCount
  }

  private def updateFromCsv(items: Seq[(String, String)], newUrls: Seq[(String, String)]): Future[Seq[(String, String)]] = {
    updateMovedItems(items, newUrls).map { count =>
      logger.info(s"Added $count redirects")
      newUrls
    }
  }

  private def updateFromCsv(inputStream: InputStream, prefixes: String): Future[Seq[(String, String)]] = {
    val items = parseCsv(inputStream).collect { case f :: t :: _ => f -> t }
    updateFromCsv(items, remapUrlsFromPrefixes(items, prefixes))
  }

  private def updateFromCsv(items: Seq[(String, String)], prefixes: String): Future[Seq[(String, String)]] =
    updateFromCsv(items, remapUrlsFromPrefixes(items, prefixes))


  private def parseCsv(ios: InputStream, sep: Char = ','): Seq[List[String]] = {
    import com.fasterxml.jackson.dataformat.csv.CsvSchema

    import scala.collection.JavaConverters._

    val schema = CsvSchema.builder().setColumnSeparator(sep).build()
    val all: MappingIterator[Array[String]] = CsvHelpers
      .mapper
      .enable(CsvParser.Feature.WRAP_AS_ARRAY)
      .readerFor(classOf[Array[String]])
      .`with`(schema)
      .readValues(ios)
    try for {
        arr <- all.readAll().asScala
      } yield arr.toList finally {
      all.close()
    }
  }
}
