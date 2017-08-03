package controllers.admin

import java.io.{FileInputStream, InputStream}
import java.nio.charset.StandardCharsets
import javax.inject._

import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvParser
import controllers.AppComponents
import controllers.base.AdminController
import defines.ContentTypes
import models.admin.IngestParams
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.HeaderNames
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents, MultipartFormData}
import services.cypher.Cypher
import services.data.{AuthenticatedUser, Constants}
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
  private val logger = play.api.Logger(getClass)

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
    Ok(views.html.admin.utils.movedItemsForm(urlMapForm,
      controllers.admin.routes.Utils.addMovedItemsPost()))
  }

  def addMovedItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.utils.movedItemsForm(errForm,
          controllers.admin.routes.Utils.addMovedItemsPost()))),
        prefix => request.body.file("csv").map { file =>
          updateFromCsv(new FileInputStream(file.ref.path.toFile), prefix)
            .map(newUrls => Ok(views.html.admin.utils.movedItemsAdded(newUrls)))
        }.getOrElse {
          immediate(Ok(views.html.admin.utils.movedItemsForm(
            boundForm.withError("csv", "No CSV file found"),
            controllers.admin.routes.Utils.addMovedItemsPost())))
        }
      )
    }

  def renameItems(): Action[AnyContent] = AdminAction { implicit request =>
    Ok(views.html.admin.utils.renameItemsForm(urlMapForm,
      controllers.admin.routes.Utils.renameItemsPost()))
  }

  def renameItemsPost(): Action[MultipartFormData[TemporaryFile]] =
    AdminAction.async(parsers.multipartFormData) { implicit request =>
      val boundForm: Form[String] = urlMapForm.bindFromRequest
      boundForm.fold(
        errForm => immediate(BadRequest(views.html.admin.utils
          .renameItemsForm(errForm, controllers.admin.routes.Utils.renameItemsPost()))),
        prefix => request.body.file("csv").map { file =>
          val todo = parseCsv(new FileInputStream(file.ref.path.toFile))
            .collect { case from :: to :: _ => from -> to }
          userDataApi.rename(todo).flatMap { items =>
            updateFromCsv(items, prefix)
              .map(newUrls => Ok(views.html.admin.utils.movedItemsAdded(newUrls)))
          }
        }.getOrElse {
          immediate(Ok(views.html.admin.utils.movedItemsForm(
            boundForm.withError("csv", "No CSV file found"),
            controllers.admin.routes.Utils.renameItemsPost())))
        }
      )
    }

  private val regenerateForm: Form[(Option[ContentTypes.Value], Option[String])] = Form(
    tuple(
      "type" -> optional(EnumUtils.enumMapping(ContentTypes)),
      "scope" -> optional(nonEmptyText).transform (_.flatMap {
          case "" => Option.empty
          case s => Some(s)
        }, identity[Option[String]])
    ).verifying("Choose one option OR the other", t => !(t._1.isDefined && t._2.isDefined))
  )

  def regenerateIds(): Action[AnyContent] = AdminAction.apply { implicit request =>
    regenerateForm.bindFromRequest.fold(
      err => BadRequest(views.html.admin.utils.regenerate(err,
        controllers.admin.routes.Utils.regenerateIds())), {
        case (Some(et), None) => Redirect(controllers.admin.routes.Utils.regenerateIdsForType(et))
        case (None, Some(id)) => Redirect(controllers.admin.routes.Utils.regenerateIdsForScope(id))
        case _ => Ok(views.html.admin.utils.regenerate(regenerateForm,
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

  def regenerateIdsForType(ct: defines.ContentTypes.Value): Action[AnyContent] = AdminAction.async { implicit request =>
    if (isAjax) userDataApi.regenerateIdsForType(ct).map { items =>
      Ok(views.html.admin.utils.regenerateIdsForm(regenerateIdsForm
        .fill("" -> items.map { case (f, t) => (f, t, true) }),
        controllers.admin.routes.Utils.regenerateIdsPost()))
    } else immediate(Ok(views.html.admin.utils.regenerateIds(regenerateIdsForm,
      controllers.admin.routes.Utils.regenerateIdsForType(ct))))
  }

  def regenerateIdsForScope(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    if (isAjax) userDataApi.regenerateIdsForScope(id).map { items =>
        Ok(views.html.admin.utils.regenerateIdsForm(regenerateIdsForm
          .fill("" -> items.map { case (f, t) => (f, t, true) }),
          controllers.admin.routes.Utils.regenerateIdsPost()))
    } else immediate(Ok(views.html.admin.utils.regenerateIds(regenerateIdsForm,
      controllers.admin.routes.Utils.regenerateIdsForScope(id))))
  }

  private val parser = parsers.anyContent(maxLength = Some(5 * 1024 * 1024L))
  def regenerateIdsPost(): Action[AnyContent] = AdminAction.async(parser) { implicit request =>
    val boundForm: Form[(String, Seq[(String, String, Boolean)])] = regenerateIdsForm.bindFromRequest()
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.utils.regenerateIds(errForm,
        controllers.admin.routes.Utils.regenerateIdsPost()))), {
      case (prefix, items) =>
        val activeIds = items.collect { case (f, _, true) => f }
        logger.info(s"Renaming: $activeIds")
        userDataApi.regenerateIds(activeIds, commit = true).flatMap { items =>
          updateFromCsv(items, prefix)
            .map(newUrls => Ok(views.html.admin.utils.movedItemsAdded(newUrls)))
        }
      }
    )
  }

  import models.admin.FindReplaceTask

  def findReplace: Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.utils.findReplace(FindReplaceTask.form, None,
      controllers.admin.routes.Utils.findReplacePost(),
      controllers.admin.routes.Utils.findReplacePost(commit = true)))
  }

  def findReplacePost(commit: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    val boundForm = FindReplaceTask.form.bindFromRequest()
    boundForm.fold(
      errForm => immediate(
        BadRequest(views.html.admin.utils.findReplace(errForm, None,
          controllers.admin.routes.Utils.findReplacePost(),
          controllers.admin.routes.Utils.findReplacePost(commit = true)))
      ),
      task => userDataApi.findReplace(
          task.parentType, task.subType, task.property, task.find,
          task.replace, commit, task.log).flatMap { found =>
        if (commit) {
          searchIndexer.handle.indexIds(found.map(_._1): _*).map { _ =>
            Redirect(controllers.admin.routes.Utils.findReplace())
              .flashing("success" -> Messages("admin.utils.findReplace.done", found.size))
          }
        }
        else immediate(Ok(views.html.admin.utils.findReplace(boundForm, Some(found),
          controllers.admin.routes.Utils.findReplacePost(),
          controllers.admin.routes.Utils.findReplacePost(commit = true))))
      }
    )
  }


  def ingestPost(id: String, dataType: String): Action[MultipartFormData[TemporaryFile]] = AdminAction.async(parse.multipartFormData) { implicit request =>
    val boundForm = IngestParams.ingestForm.bindFromRequest()
    request.body.file(IngestParams.DATA_FILE).map { data =>
      boundForm.fold(
        errForm => immediate(BadRequest(Json.obj("form" -> errForm.errorsAsJson))),
        ingestTask => {
          // We only want XML types here, everything else is just binary
          val ct = data.contentType.filter(_.endsWith("xml"))
            .getOrElse(play.api.http.ContentTypes.BINARY)
          // NB: Overcomplicated due to https://github.com/playframework/playframework/issues/6203
          val props: Option[java.io.File] = request.body.file(IngestParams.PROPERTIES_FILE)
            .flatMap(f => if (f.filename.nonEmpty) Some(f.ref.path.toFile) else None)

          ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/import/$dataType")
            .addHttpHeaders(Constants.AUTH_HEADER_NAME -> request.user.id)
            .addHttpHeaders(HeaderNames.CONTENT_TYPE -> ct)
            .addQueryStringParameters("scope" -> id)
            .addQueryStringParameters(ingestTask.copy(properties = props).toParams: _*)
            .post(data.ref.path.toFile).map { r =>
            r.status match {
              case OK => if (isAjax) Ok(r.json) else Ok(r.body)
              case BAD_REQUEST => BadRequest(r.body)
              case _ => InternalServerError(r.body)
            }

          }
        }
      )
    }.getOrElse(immediate(BadRequest(
      Json.obj("form" -> boundForm.withError(IngestParams.DATA_FILE, "required").errorsAsJson))))
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
      _ <- searchIndexer.handle.clearIds(items.map(_._1): _*)
      // Index the new ones....
      _ <- searchIndexer.handle.indexIds(items.map(_._2): _*)
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
