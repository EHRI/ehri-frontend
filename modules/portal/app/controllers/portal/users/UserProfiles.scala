package controllers.portal.users

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import models._
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.{JsValue, Json, JsObject}
import utils.{SessionPrefs, PageParams}
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.libs.Files.TemporaryFile
import play.api.Play.current
import java.io.{StringWriter, File}
import scala.concurrent.Future
import views.html.p
import utils.search.{Resolver, Dispatcher}
import backend.Backend

import com.google.inject._
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import play.api.mvc.MaxSizeExceeded
import play.api.mvc.MultipartFormData.FilePart
import controllers.DataFormat
import play.api.http.{HeaderNames, MimeTypes}
import org.joda.time.format.ISODateTimeFormat
import models.base.AnyModel
import net.coobird.thumbnailator.Thumbnails
import com.typesafe.plugin.MailerAPI
import controllers.portal.base.{PortalController, PortalAuthConfigImpl}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class UserProfiles @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountManager, mailer: MailerAPI)
    extends PortalController
    with LoginLogout
    with PortalAuthConfigImpl {

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile

  private val profileRoutes = controllers.portal.users.routes.UserProfiles

  def watchItem(id: String) = WithUserAction { implicit request =>
    Ok(p.helpers.simpleForm("profile.watch",
      profileRoutes.watchItemPost(id)))
  }

  def watchItemPost(id: String) = WithUserAction.async { implicit request =>
    backend.watch(request.user.id, id).map { _ =>
      clearWatchedItemsCache(request.user.id)
      if (isAjax) Ok("ok")
      else Redirect(profileRoutes.watching())
    }
  }

  def unwatchItem(id: String) = WithUserAction { implicit request =>
    Ok(p.helpers.simpleForm("profile.unwatch",
      profileRoutes.unwatchItemPost(id)))
  }

  def unwatchItemPost(id: String) = WithUserAction.async { implicit request =>
    backend.unwatch(request.user.id, id).map { _ =>
      clearWatchedItemsCache(request.user.id)
      if (isAjax) Ok("ok")
      else Redirect(profileRoutes.watching())
    }
  }

  case class ExportWatchItem(
    name: String,
    url: String
  ) {
    def toCsv: Array[String] = Array(name, url)
  }

  object ExportWatchItem {
    def fromItem(item: AnyModel)(implicit request: RequestHeader): ExportWatchItem = new ExportWatchItem(
      item.toStringLang,
      views.p.Helpers.linkTo(item).absoluteURL(globalConfig.https)
    )
    implicit val writes = Json.writes[ExportWatchItem]
  }

  def watching(format: DataFormat.Value = DataFormat.Html) = WithUserAction.async { implicit request =>
    val watchParams = PageParams.fromRequest(request, namespace = "w")
    backend.watching[AnyModel](request.user.id, watchParams).map { watchList =>
      format match {
        case DataFormat.Text => Ok(views.txt.p.userProfile.watchedItems(watchList))
            .as(MimeTypes.TEXT)
        case DataFormat.Csv => Ok(writeCsv(
          List("Item", "URL"),
          watchList.items.map(a => ExportWatchItem.fromItem(a).toCsv)))
          .as("text/csv")
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=${request.user.id}_watched.csv")
        case DataFormat.Json =>
          Ok(Json.toJson(watchList.items.map(ExportWatchItem.fromItem)))
            .as(MimeTypes.JSON)

        case _ => Ok(p.userProfile.watchedItems(watchList))
      }
    }
  }

  // A condensed version of an annotation for export
  case class ExportAnnotation(target: Option[String], field: Option[String], body: String,
                              time: Option[String], url: Option[String]) {
    // NB: Using nulls here because the OpenCSV expects them in
    // the absence of a value (rather than, say, empty strings)
    def toCsv: Array[String] = Array(
      target.orNull,
      field.orNull,
      body,
      time.orNull,
      url.orNull
    )
  }

  object ExportAnnotation {
    def fromAnnotation(annotation: Annotation)(implicit request: RequestHeader): ExportAnnotation = new ExportAnnotation(
      annotation.target.map(_.toStringLang),
      annotation.model.field,
      annotation.model.body,
      annotation.latestEvent
        .map(_.model.timestamp.toString(ISODateTimeFormat.dateTime)),
      annotation.target
        .map(t => views.p.Helpers.linkTo(t).absoluteURL(globalConfig.https) + "#" + annotation.id)
    )
    import play.api.libs.json._
    implicit val writes = Json.writes[ExportAnnotation]
  }

  def annotations(format: DataFormat.Value = DataFormat.Html) = WithUserAction.async { implicit request =>
    val params = PageParams.fromRequest(request)
    backend.userAnnotations[Annotation](request.user.id, params).map { page =>
      format match {
        case DataFormat.Text =>
          Ok(views.txt.p.userProfile.annotations(page).body.trim)
            .as(MimeTypes.TEXT)
        case DataFormat.Csv => Ok(writeCsv(
            List("Item", "Field", "Note", "Time", "URL"),
            page.items.map(a => ExportAnnotation.fromAnnotation(a).toCsv)))
          .as("text/csv")
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=${request.user.id}_notes.csv")
        case DataFormat.Json =>
          Ok(Json.toJson(page.items.map(ExportAnnotation.fromAnnotation)))
            .as(MimeTypes.JSON)
        case _ => Ok(p.userProfile.annotations(page))
      }
    }
  }

  def writeCsv(headers: Seq[String], data: Seq[Array[String]])(implicit request: RequestHeader): String = {
    import au.com.bytecode.opencsv.CSVWriter
    val buffer = new StringWriter()
    val csvWriter = new CSVWriter(buffer)
    csvWriter.writeNext(headers.toArray)
    for (item <- data) {
      csvWriter.writeNext(item)
    }
    csvWriter.close()
    buffer.getBuffer.toString
  }

  def annotationListToJson(annotations: Seq[Annotation])(implicit request: RequestHeader): JsValue
    = Json.toJson(annotations.map(ExportAnnotation.fromAnnotation))

  def prefs = Action { implicit request =>
    Ok(Json.toJson(preferences))
  }

  def updatePrefs() = Action { implicit request =>
    SessionPrefs.updateForm(request.preferences).bindFromRequest.fold(
      errors => BadRequest(errors.errorsAsJson),
      updated => {
        (if (isAjax) Ok(Json.toJson(updated))
        else Redirect(profileRoutes.prefs())).withPreferences(updated)
      }
    )
  }

  def profile = WithUserAction.async { implicit request =>
    val annParams = PageParams.fromRequest(request, namespace = "a")
    val annotationsF = backend.userAnnotations[Annotation](request.user.id, annParams)
    for {
      anns <- annotationsF
    } yield Ok(p.userProfile.notes(request.user, anns, followed = false, canMessage = false))
  }

  import play.api.data.Form
  import play.api.data.Forms._
  private def deleteForm(user: UserProfile): Form[String] = Form(
    single(
      "confirm" -> nonEmptyText.verifying("profile.delete.badConfirmation", f => f match {
        case name =>
          user.model.name.toLowerCase.trim == name.toLowerCase.trim
      })
    )
  )

  private val imageForm = Form(
    single("image" -> text)
  )

  private def profileDataForm(implicit userOpt: Option[UserProfile]): Form[ProfileData] = {
    userOpt.map { user =>
      ProfileData.form.fill(ProfileData.fromUser(user))
    } getOrElse {
      ProfileData.form
    }
  }

  private def accountPrefsForm(implicit userOpt: Option[UserProfile]): Form[AccountPreferences] = {
    userOpt.flatMap(_.account).map { account =>
      AccountPreferences.form.fill(AccountPreferences.fromAccount(account))
    } getOrElse {
      AccountPreferences.form
    }
  }

  def updateAccountPrefsPost() = WithUserAction.async { implicit request =>
    AccountPreferences.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(p.userProfile.editProfile(
            ProfileData.form, imageForm, errForm))),
      accountPrefs => userDAO.findById(request.user.id).flatMap {
        case Some(account) =>
          userDAO.update(account.copy(allowMessaging = accountPrefs.allowMessaging)).map { _ =>
            Redirect(profileRoutes.updateProfile())
              .flashing("success" -> "profile.preferences.updated")
          }
        case _ => authenticationFailed(request)
      }
    )
  }

  def updateProfile() = WithUserAction { implicit request =>
    Ok(p.userProfile.editProfile(profileDataForm, imageForm, accountPrefsForm))
  }

  def updateProfilePost() = WithUserAction.async { implicit request =>
    ProfileData.form.bindFromRequest.fold(
      errForm => immediate(
        BadRequest(p.userProfile.editProfile(errForm, imageForm, accountPrefsForm))
      ),
      profile => backend.patch[UserProfile](request.user.id, Json.toJson(profile).as[JsObject]).map { userProfile =>
        Redirect(profileRoutes.profile())
          .flashing("success" -> Messages("profile.update.confirmation"))
      }
    )
  }

  def deleteProfile() = WithUserAction { implicit request =>
    Ok(p.userProfile.deleteProfile(deleteForm(request.user),
      profileRoutes.deleteProfilePost()))
  }

  def deleteProfilePost() = WithUserAction.async { implicit request =>
    deleteForm(request.user).bindFromRequest.fold(
      errForm => immediate(BadRequest(p.userProfile.deleteProfile(
        errForm.withGlobalError("profile.delete.badConfirmation"),
        profileRoutes.deleteProfilePost()))),
      _ => {
        // Here we are not going to delete their whole profile, since
        // that would destroy the record of the user's activities and
        // the provenance of the data. Instead we just anonymize it by
        // updating the record with minimal information
        val anonProfile = UserProfileF(id = Some(request.user.id),
          identifier = request.user.model.identifier, name = request.user.model.identifier,
          active = false)

        backend.update(request.user.id, anonProfile).flatMap { bool =>
          userDAO.delete(request.user.id).flatMap { _ =>
            gotoLogoutSucceeded
              .map(_.flashing("success" -> "profile.profile.delete.confirmation"))
          }
        }
      }
    )
  }

  // Defer to the standard profile update page...
  def updateProfileImage() = updateProfile()

  // Body parser that'll refuse anything larger than 5MB
  private def uploadParser = parse.maxLength(5 * 1024 * 1024, parse.multipartFormData)

  def updateProfileImagePost() = WithUserAction.async(uploadParser) { implicit request =>

    def onError(err: String): Future[Result] = immediate(
        BadRequest(p.userProfile.editProfile(profileDataForm,
          imageForm.withGlobalError(err), accountPrefsForm)))

    request.body match {
      case Left(MaxSizeExceeded(length)) => onError("errors.imageTooLarge")
      case Right(multipartForm) => multipartForm.file("image").map { file =>
        if (isValidContentType(file)) {
          try {
            for {
              url <- convertAndUploadFile(file, request.user, request)
              _ <- backend.patch(request.user.id, Json.obj(UserProfileF.IMAGE_URL -> url))
            } yield Redirect(profileRoutes.profile())
                  .flashing("success" -> "profile.update.confirmation")
          } catch {
            case e: UnsupportedFormatException => onError("errors.badFileType")
          }
        } else {
          onError("errors.badFileType")
        }
      }.getOrElse {
        onError("errors.noFileGiven")
      }
    }
  }

  private def isValidContentType(file: FilePart[TemporaryFile]): Boolean
    = file.contentType.exists(_.toLowerCase.startsWith("image/"))

  private def convertAndUploadFile(file: FilePart[TemporaryFile], user: UserProfile, request: RequestHeader): Future[String] = {
    import awscala._
    import awscala.s3._
    // Ugh, this API is ugly... or maybe it's just how I'm using it...?
    val bucketName: String = current.configuration.getString("aws.bucket")
      .getOrElse(sys.error("Invalid configuration: no aws.bucket key found"))
    val region: String = current.configuration.getString("s3.region")
      .getOrElse(sys.error("Invalid configuration: no aws.region key found"))
    val instanceName: String = current.configuration.getString("aws.instance")
      .getOrElse(request.host)
    val accessKey =current.configuration.getString("aws.accessKeyId")
      .getOrElse(sys.error("Invalid configuration: no aws.accessKeyId found"))
    val secret =current.configuration.getString("aws.secretKey")
      .getOrElse(sys.error("Invalid configuration: no aws.secretKey found"))

    implicit val s3 = S3(Credentials(accessKey, secret)).at(awscala.Region(region))

    val bucket: Bucket = s3.bucket(bucketName)
      .getOrElse(sys.error(s"Bucket $bucketName not found"))
    val extension = file.filename.substring(file.filename.lastIndexOf("."))
    val awsName = s"images/$instanceName/${user.id}$extension"
    val temp = File.createTempFile(user.id, extension)
    Thumbnails.of(file.ref.file).size(200, 200).toFile(temp)

    val read: PutObjectResult = bucket.putAsPublicRead(awsName, temp)
    Future.successful(s"http://${read.bucket.name}.s3-$region.amazonaws.com/${read.key}")
  }
}
