package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
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

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Profile @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountDAO)
    extends LoginLogout with AuthController with ControllerHelpers
    with PortalBase
    with PortalLogin
    with PortalAuthConfigImpl
    with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile

  private val profileRoutes = controllers.portal.routes.Profile

  def account = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt.flatMap(_.account)))
  }

  def watchItem(id: String) = withUserAction { implicit user => implicit request =>
    Ok(p.helpers.simpleForm("portal.profile.watch",
      profileRoutes.watchItemPost(id)))
  }

  def watchItemPost(id: String) = withUserAction.async { implicit user => implicit request =>
    backend.watch(user.id, id).map { _ =>
      clearWatchedItemsCache(user.id)
      if (isAjax) Ok("ok")
      else Redirect(profileRoutes.watching())
    }
  }

  def unwatchItem(id: String) = withUserAction { implicit user => implicit request =>
    Ok(p.helpers.simpleForm("portal.profile.unwatch",
      profileRoutes.unwatchItemPost(id)))
  }

  def unwatchItemPost(id: String) = withUserAction.async { implicit user => implicit request =>
    backend.unwatch(user.id, id).map { _ =>
      clearWatchedItemsCache(user.id)
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

  def watching(format: DataFormat.Value = DataFormat.Html) = withUserAction.async { implicit user => implicit request =>
    val watchParams = PageParams.fromRequest(request, namespace = "w")
    backend.watching(user.id, watchParams).map { watchList =>
      format match {
        case DataFormat.Text => Ok(views.txt.p.profile.watchedItems(watchList))
            .as(MimeTypes.TEXT)
        case DataFormat.Csv => Ok(writeCsv(
          List("Item", "URL"),
          watchList.items.map(a => ExportWatchItem.fromItem(a).toCsv)))
          .as("text/csv")
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=${user.id}_watched.csv")
        case DataFormat.Json =>
          Ok(Json.toJson(watchList.items.map(ExportWatchItem.fromItem)))
            .as(MimeTypes.JSON)

        case _ => Ok(p.profile.watchedItems(watchList))
      }
    }
  }

  // A condensed version of an annotation for export
  case class ExportAnnotation(target: Option[String], field: Option[String], body: String,
                              time: Option[String], url: Option[String]) {
    // NB: Using nulls here because the OpenCSV expects them in
    // the absence of a value (rather than, say, empty strings)
    def toCsv: Array[String] = Array(
      target.getOrElse(null),
      field.getOrElse(null),
      body,
      time.getOrElse(null),
      url.getOrElse(null)
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

  def annotations(format: DataFormat.Value = DataFormat.Html) = withUserAction.async { implicit user => implicit request =>
    val params = PageParams.fromRequest(request)
    backend.userAnnotations(user.id, params).map { page =>
      format match {
        case DataFormat.Text =>
          Ok(views.txt.p.profile.annotations(page).body.trim)
            .as(MimeTypes.TEXT)
        case DataFormat.Csv => Ok(writeCsv(
            List("Item", "Field", "Note", "Time", "URL"),
            page.items.map(a => ExportAnnotation.fromAnnotation(a).toCsv)))
          .as("text/csv")
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=${user.id}_notes.csv")
        case DataFormat.Json =>
          Ok(Json.toJson(page.items.map(ExportAnnotation.fromAnnotation)))
            .as(MimeTypes.JSON)
        case _ => Ok(p.profile.annotations(page))
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

  def profile = withUserAction.async { implicit user => implicit request =>
    val annParams = PageParams.fromRequest(request, namespace = "a")
    val annotationsF = backend.userAnnotations(user.id, annParams)
    for {
      anns <- annotationsF
    } yield Ok(p.profile.profile(anns))
  }

  import play.api.data.Form
  import play.api.data.Forms._
  private def deleteForm(user: UserProfile): Form[String] = Form(
    single(
      "confirm" -> nonEmptyText.verifying("portal.profile.deleteProfile.badConfirmation", f => f match {
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

  /**
   * Store a changed password.
   */
  def changePasswordPost = changePasswordPostAction { boolOrErr => implicit userOpt => implicit request =>
    boolOrErr match {
      case Right(true) =>
        Redirect(defaultLoginUrl)
          .flashing("success" -> Messages("login.passwordChanged"))
      case Right(false) =>
        BadRequest(p.profile.editProfile(
          profileDataForm, imageForm, changePasswordForm
            .withGlobalError("login.badUsernameOrPassword"), AccountPreferences.form))
      case Left(errForm) =>
        BadRequest(p.profile.editProfile(
          profileDataForm, imageForm, errForm, accountPrefsForm))
    }
  }

  def updateAccountPrefsPost() = withUserAction { implicit user => implicit request =>
    AccountPreferences.form.bindFromRequest.fold(
      errForm => BadRequest(p.profile.editProfile(
        ProfileData.form, imageForm, changePasswordForm, errForm)),
      accountPrefs => {
        userDAO.findByProfileId(user.id).map { acc =>
          acc.setAllowMessaging(accountPrefs.allowMessaging)
        }
        Redirect(profileRoutes.updateProfile())
          .flashing("success" -> "portal.profile.preferences.updated")
      }
    )
  }

  def updateProfile() = withUserAction { implicit user => implicit request =>
    Ok(p.profile.editProfile(
      profileDataForm, imageForm, changePasswordForm, accountPrefsForm))
  }

  def updateProfilePost() = withUserAction.async { implicit user => implicit request =>
    ProfileData.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(p.profile.editProfile(
        errForm, imageForm, changePasswordForm, accountPrefsForm))),
      profile => backend.patch[UserProfile](user.id, Json.toJson(profile).as[JsObject]).map { userProfile =>
        Redirect(profileRoutes.profile())
          .flashing("success" -> Messages("profile.update.confirmation"))
      }
    )
  }


  def deleteProfile() = withUserAction { implicit user => implicit request =>
    Ok(p.profile.deleteProfile(deleteForm(user),
      profileRoutes.deleteProfilePost()))
  }

  def deleteProfilePost() = withUserAction.async { implicit user => implicit request =>
    deleteForm(user).bindFromRequest.fold(
      errForm => immediate(BadRequest(p.profile.deleteProfile(
        errForm.withGlobalError("portal.profile.deleteProfile.badConfirmation"),
        profileRoutes.deleteProfilePost()))),
      _ => {
        // Here we are not going to delete their whole profile, since
        // that would destroy the record of the user's activities and
        // the provenance of the data. Instead we just anonymize it by
        // updating the record with minimal information
        val anonProfile = UserProfileF(id = Some(user.id),
          identifier = user.model.identifier, name = user.model.identifier,
          active = false)
        backend.update(user.id, anonProfile).flatMap { bool =>
          user.account.get.delete()
          gotoLogoutSucceeded
            .map(_.flashing("success" -> "portal.profile.profileDeleted"))
        }
      }
    )
  }

  // Defer to the standard profile update page...
  def updateProfileImage() = updateProfile()

  // Body parser that'll refuse anything larger than 5MB
  private def uploadParser = parse.maxLength(5 * 1024 * 1024, parse.multipartFormData)

  def updateProfileImagePost() = withUserAction.async(uploadParser) { implicit user => implicit request =>

    def onError(err: String) =
      BadRequest(p.profile.editProfile(profileDataForm,
        imageForm.withGlobalError(s"portal.error.$err"),
        changePasswordForm, accountPrefsForm))

    request.body match {
      case Left(MaxSizeExceeded(length)) => immediate(onError("imageTooLarge"))
      case Right(multipartForm) => multipartForm.file("image").map { file =>
        if (isValidContentType(file)) {
          try {
            for {
              url <- convertAndUploadFile(file, user, request)
              _ <- backend.patch(user.id, Json.obj(UserProfileF.IMAGE_URL -> url))
            } yield Redirect(profileRoutes.profile())
                  .flashing("success" -> "profile.update.confirmation")
          } catch {
            case e: UnsupportedFormatException => immediate(onError("badFileType"))
          }
        } else {
          immediate(onError("badFileType"))
        }
      }.getOrElse {
        immediate(onError("noFileGiven"))
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
