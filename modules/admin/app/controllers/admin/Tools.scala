package controllers.admin

import java.nio.charset.StandardCharsets

import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import defines.ContentTypes
import javax.inject._
import models.admin.BatchDeleteTask
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents, MultipartFormData}
import services.cypher.CypherService
import services.data.InputDataError
import services.search.SearchIndexMediator
import utils.EnumUtils

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


/**
  * Controller for various monitoring functions and admin utilities.
  */
@Singleton
case class Tools @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient,
  cypher: CypherService
)(implicit mat: Materializer) extends AdminController {

  private def logger = play.api.Logger(classOf[Tools])


  private val pathPrefixField = nonEmptyText.verifying("isPath",
    s => s.split(',').forall(_.startsWith("/") && s.endsWith("/")))

  private val urlMapForm = Form(
    single("path-prefix" -> pathPrefixField)
  )

  def addMovedItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.tools.movedItemsForm(urlMapForm,
      controllers.admin.routes.Tools.addMovedItemsPost()))
  }

  def addMovedItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.tools.movedItemsForm(errForm,
          controllers.admin.routes.Tools.addMovedItemsPost()))),
        prefix => request.body.file("csv").map { file =>
          updateFromCsv(FileIO.fromPath(file.ref.path), prefix)
            .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
        }.getOrElse {
          immediate(Ok(views.html.admin.tools.movedItemsForm(
            boundForm.withError("csv", "No CSV file found"),
            controllers.admin.routes.Tools.addMovedItemsPost())))
        }
      )
    }

  def renameItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.tools.renameItemsForm(urlMapForm,
      controllers.admin.routes.Tools.renameItemsPost()))
  }

  def renameItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.tools
          .renameItemsForm(errForm, controllers.admin.routes.Tools.renameItemsPost()))),
        prefix => request.body.file("csv").filter(_.filename.nonEmpty).map { file =>
          parseCsv(FileIO.fromPath(file.ref.path))
              .collect { case from :: to :: _ => from -> to }
              .runWith(Sink.seq).flatMap { todo =>
            userDataApi.rename(todo).flatMap { items =>
              updateFromCsv(items, prefix)
                .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
            } recover {
              case e: InputDataError =>
                BadRequest(views.html.admin.tools.renameItemsForm(
                  boundForm.withGlobalError(e.details),
                  controllers.admin.routes.Tools.renameItemsPost()))
            }
          }
        }.getOrElse {
          immediate(BadRequest(views.html.admin.tools.renameItemsForm(
            boundForm.withGlobalError("No CSV file found"),
            controllers.admin.routes.Tools.renameItemsPost())))
        }
      )
    }

  def reparentItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.tools.reparentItemsForm(urlMapForm,
      controllers.admin.routes.Tools.reparentItemsPost()))
  }

  def reparentItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.tools
          .reparentItemsForm(errForm, controllers.admin.routes.Tools.reparentItemsPost()))),
        prefix => request.body.file("csv").filter(_.filename.nonEmpty).map { file =>
          parseCsv(FileIO.fromPath(file.ref.path))
              .collect { case id :: parent :: _ => id -> parent }
              .runWith(Sink.seq).flatMap { todo =>
            userDataApi.reparent(todo, commit = true).flatMap { items =>
              updateFromCsv(items, prefix)
                .map(newUrls => Ok(views.html.admin.tools.movedItemsAdded(newUrls)))
            } recover {
              case e: InputDataError =>
                BadRequest(views.html.admin.tools.reparentItemsForm(
                  boundForm.withGlobalError(e.details),
                  controllers.admin.routes.Tools.reparentItemsPost()))
            }
          }
        }.getOrElse {
          immediate(BadRequest(views.html.admin.tools.reparentItemsForm(
            boundForm.withGlobalError("No CSV file found"),
            controllers.admin.routes.Tools.reparentItemsPost())))
        }
      )
    }

  private val regenerateForm: Form[(Option[ContentTypes.Value], Option[String], Boolean)] = Form(
    tuple(
      "type" -> optional(EnumUtils.enumMapping(ContentTypes)),
      "scope" -> optional(nonEmptyText).transform(_.flatMap {
        case "" => Option.empty
        case s => Some(s)
      }, identity[Option[String]]),
      "tolerant" -> boolean
    ).verifying("Choose one option OR the other", t => !(t._1.isDefined && t._2.isDefined))
  )

  def regenerateIds(): Action[AnyContent] = AdminAction.apply { implicit request =>
    val form = regenerateForm.bindFromRequest
    form.fold(
      err => BadRequest(views.html.admin.tools.regenerate(err, controllers.admin.routes.Tools.regenerateIds())), {
        case (Some(et), None, t) =>
          Redirect(controllers.admin.routes.Tools.regenerateIdsForType(et, t))
        case (None, Some(id), t) =>
          Redirect(controllers.admin.routes.Tools.regenerateIdsForScope(id, t))
        case d@(Some(_), Some(_), t) =>
          Ok(views.html.admin.tools.regenerate(
            regenerateForm.fill(d)
              .withGlobalError(Messages("admin.utils.regenerateIds.chooseOne")),
            controllers.admin.routes.Tools.regenerateIds()))
        case _ => Ok(views.html.admin.tools.regenerate(regenerateForm,
          controllers.admin.routes.Tools.regenerateIds()))
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
          controllers.admin.routes.Tools.regenerateIdsPost(tolerant)))
      } recover {
        case e: InputDataError =>
          Ok(views.html.admin.tools.regenerateForm(regenerateForm
            .fill((Some(ct), None, tolerant))
            .withGlobalError(e.details),
            controllers.admin.routes.Tools.regenerateIds()))
      }
    } else immediate(Ok(views.html.admin.tools.regenerateIds(regenerateIdsForm,
      controllers.admin.routes.Tools.regenerateIdsForType(ct, tolerant))))
  }

  def regenerateIdsForScope(id: String, tolerant: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    if (isAjax) {
      userDataApi.regenerateIdsForScope(id, tolerant).map { items =>
        Ok(views.html.admin.tools.regenerateIdsForm(regenerateIdsForm
          .fill(value = ("", items.map { case (f, t) => (f, t, true) })),
          controllers.admin.routes.Tools.regenerateIdsPost(tolerant)))
      }
    } else immediate(Ok(views.html.admin.tools.regenerateIds(regenerateIdsForm,
      controllers.admin.routes.Tools.regenerateIdsForScope(id))))
  }

  private val parser = parsers.anyContent(maxLength = Some(5 * 1024 * 1024L))

  def regenerateIdsPost(tolerant: Boolean): Action[AnyContent] = AdminAction.async(parser) { implicit request =>
    val boundForm: Form[(String, Seq[(String, String, Boolean)])] = regenerateIdsForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.tools.regenerateIds(errForm,
        controllers.admin.routes.Tools.regenerateIdsPost(tolerant)))), {
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
      controllers.admin.routes.Tools.findReplacePost(),
      controllers.admin.routes.Tools.findReplacePost(commit = true)))
  }

  private val indexer = searchIndexer.handle

  def findReplacePost(commit: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    val boundForm = FindReplaceTask.form.bindFromRequest()
    boundForm.fold(
      errForm => immediate(
        BadRequest(views.html.admin.tools.findReplace(errForm, None,
          controllers.admin.routes.Tools.findReplacePost(),
          controllers.admin.routes.Tools.findReplacePost(commit = true)))
      ),
      task => userDataApi.findReplace(
        task.parentType, task.subType, task.property, task.find,
        task.replace, commit, task.log).flatMap { found =>
        if (commit) {
          indexer.indexIds(found.map(_._1): _*).map { _ =>
            Redirect(controllers.admin.routes.Tools.findReplace())
              .flashing("success" -> Messages("admin.utils.findReplace.done", found.size))
          }
        }
        else immediate(Ok(views.html.admin.tools.findReplace(boundForm, Some(found),
          controllers.admin.routes.Tools.findReplacePost(),
          controllers.admin.routes.Tools.findReplacePost(commit = true))))
      }
    )
  }

  def batchDelete: Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.tools.batchDelete(BatchDeleteTask.form.fill(BatchDeleteTask()),
      controllers.admin.routes.Tools.batchDeletePost()))
  }

  def batchDeletePost: Action[AnyContent] = AdminAction.async { implicit request =>
    val boundForm = BatchDeleteTask.form.bindFromRequest
    boundForm.fold(
      err => immediate(BadRequest(views.html.admin.tools.batchDelete(err,
        controllers.admin.routes.Tools.batchDeletePost()))),
      data => userDataApi.batchDelete(
        data.ids, data.scope, data.log, version = data.version, commit = data.commit
      ).map { deleted =>
        Redirect(controllers.admin.routes.Tools.batchDelete())
          .flashing("success" -> Messages("admin.utils.batchDelete.done", deleted))
      } recover {
        case e: InputDataError =>
          BadRequest(views.html.admin.tools.batchDelete(boundForm.withGlobalError(e.details),
            controllers.admin.routes.Tools.batchDeletePost()))
      }
    )
  }

  private val redirectForm: Form[(String, String)] = Form(
    tuple(
      "from" -> nonEmptyText.verifying("admin.utils.redirect.badPathError", p => p.startsWith("/")),
      "to" -> nonEmptyText.verifying("admin.utils.redirect.badPathError", p => p.startsWith("/"))
    )
  )

  def redirect: Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.tools.redirectForm(redirectForm,
      controllers.admin.routes.Tools.redirectPost()))
  }

  def redirectPost: Action[AnyContent] = AdminAction.async { implicit request =>
    val boundForm = redirectForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.tools.redirectForm(errForm,
        controllers.admin.routes.Tools.redirectPost()))),
      fromTo => appComponents.pageRelocator.addMoved(Seq(fromTo)).map { _ =>
        Redirect(controllers.admin.routes.Tools.redirect())
          .flashing("success" -> Messages("admin.utils.redirect.done"))
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

  private def updateFromCsv(src: Source[ByteString, _], prefixes: String): Future[Seq[(String, String)]] = {
    parseCsv(src)
      .collect { case f :: t :: _ => f -> t }
      .runWith(Sink.seq).flatMap { items =>
      updateFromCsv(items, remapUrlsFromPrefixes(items, prefixes))
    }
  }

  private def updateFromCsv(items: Seq[(String, String)], prefixes: String): Future[Seq[(String, String)]] =
    updateFromCsv(items, remapUrlsFromPrefixes(items, prefixes))


  private def parseCsv[M](src: Source[ByteString, M], sep: Char = ','): Source[List[String], M] = {
    src.via(CsvParsing.lineScanner(sep.toByte)).map(_.map(_.utf8String))
  }
}
