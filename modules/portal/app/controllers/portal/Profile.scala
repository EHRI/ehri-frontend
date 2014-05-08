package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models._
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.Json
import utils.{SessionPrefs, PageParams}
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.libs.Files.TemporaryFile
import play.api.Play.current
import java.io.File
import org.apache.commons.io.FileUtils
import play.api.libs.json.JsObject
import play.Logger
import scala.concurrent.Future
import play.api.mvc.MultipartFormData.FilePart
import views.html.p
import utils.search.{Resolver, Dispatcher}
import backend.Backend

import com.google.inject._
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import play.api.data.Forms
import play.api.mvc.MaxSizeExceeded
import play.api.mvc.MultipartFormData.FilePart
import scala.Some
import play.api.libs.json.JsObject

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Profile @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                            userDAO: AccountDAO)
    extends LoginLogout with AuthController with ControllerHelpers
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
    val watchParams = PageParams.fromRequest(request, namespace = "watch")
    val linkParams = PageParams.fromRequest(request, namespace = "link")
    val annParams = PageParams.fromRequest(request, namespace = "ann")
  
    for {
      watchList <- backend.pageWatching(user.id, watchParams)
      links <- backend.userLinks(user.id, linkParams)
      anns <- backend.userAnnotations(user.id, annParams)
    } yield Ok(p.profile.profile(watchList, anns, links))
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
          .flashing("success" -> Messages("confirmations.profileUpdated"))
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
        val anonymous = UserProfileF(id = Some(user.id),
          identifier = user.model.identifier, name = user.model.identifier,
          active = false)
        backend.update(user.id, anonymous).flatMap { bool =>
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
    import fly.play.s3._

    def onError(err: String) =
      BadRequest(p.profile.editProfile(profileDataForm,
        imageForm.withGlobalError(s"portal.error.$err"),
        changePasswordForm, accountPrefsForm))

    request.body match {
      case Left(MaxSizeExceeded(length)) => immediate(onError("imageTooLarge"))
      case Right(multipartForm) => multipartForm.file("image").map { file =>
        if (isValidContentType(file)) {
          try {
            convertAndUploadFile(file, user, request).flatMap { url =>
              backend.patch(user.id, Json.obj(UserProfileF.IMAGE_URL -> url)).map { _ =>
                Redirect(profileRoutes.profile())
                  .flashing("success" -> "confirmations.profileUpdated")
              }
            }.recover {
              case S3Exception(status, code, message, originalXml) =>
                Logger.error(s"$originalXml")
                Logger.error("Error: " + message)
                BadRequest(message)
            }
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
    import fly.play.s3._
    import fly.play.s3.BucketFile
    import net.coobird.thumbnailator.Thumbnails

    val bucketName: String = current.configuration.getString("aws.bucket")
      .getOrElse(sys.error("Invalid configuration: no aws.bucket key found"))
    val instanceName: String = current.configuration.getString("aws.instance")
      .getOrElse(request.host)

    val bucket = S3(bucketName)
    val ctype = file.contentType.getOrElse("application/octet-stream")
    val extension = file.filename.substring(file.filename.lastIndexOf("."))
    val awsName = s"images/$instanceName/${user.id}$extension"
    val temp = File.createTempFile(user.id, extension)
    Thumbnails.of(file.ref.file).size(200, 200).toFile(temp)
    val bis = FileUtils.readFileToByteArray(temp)

    val bucketFile: BucketFile = new BucketFile(awsName, ctype, bis)
    val upload: Future[Unit] = bucket.add(bucketFile)

    // Try and ensure we clean up afterwards...
    upload.onComplete { unit =>
      temp.delete()
      file.ref.file.delete()
    }
    upload.map(_ => bucket.url(awsName))
  }
}
