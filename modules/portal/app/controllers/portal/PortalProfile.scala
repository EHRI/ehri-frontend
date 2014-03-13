package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.{ProfileData, UserProfile, UserProfileF}
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.Json
import utils.{SessionPrefs, PageParams}
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import fly.play.s3._
import play.api.libs.Files.TemporaryFile
import play.api.Play._
import java.io.File
import net.coobird.thumbnailator.Thumbnails
import org.apache.commons.io.FileUtils
import fly.play.s3.BucketFile
import play.api.libs.json.JsObject
import play.Logger
import scala.concurrent.Future
import play.api.mvc.MultipartFormData.FilePart
import views.html.p

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalProfile extends PortalLogin {
  self: Controller with ControllerHelpers with LoginLogout with AuthController with SessionPreferences[SessionPrefs] =>

  implicit val resource = UserProfile.Resource
  val entityType = EntityType.UserProfile
  val contentType = ContentTypes.UserProfile

  private val portalRoutes = controllers.portal.routes.Portal

  def prefs = Action { implicit request =>
    Ok(Json.toJson(preferences))
  }

  def updatePrefs() = Action { implicit request =>
    SessionPrefs.updateForm(request.preferences).bindFromRequest.fold(
      errors => BadRequest(errors.errorsAsJson),
      updated => {
        (if (isAjax) Ok(Json.toJson(updated))
        else Redirect(controllers.portal.routes.Portal.prefs()))
          .withPreferences(updated)
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

  def watching = withUserAction.async { implicit user => implicit request =>
    val watchParams = PageParams.fromRequest(request)
    backend.pageWatching(user.id, watchParams).map { watchList =>
      Ok(p.profile.watchedItems(watchList))
    }
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
          ProfileData.form, changePasswordForm
            .withGlobalError("login.badUsernameOrPassword")))
      case Left(errForm) =>
        BadRequest(p.profile.editProfile(
          ProfileData.form, errForm))
    }
  }

  def updateProfile() = withUserAction { implicit user => implicit request =>
    val form = ProfileData.form.fill(ProfileData.fromUser(user))
      Ok(p.profile.editProfile(
        form, changePasswordForm))
  }

  def updateProfilePost() = withUserAction.async { implicit user => implicit request =>
    ProfileData.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(p.profile.editProfile(
        errForm, changePasswordForm))),
      profile => backend.patch[UserProfile](user.id, Json.toJson(profile).as[JsObject]).map { userProfile =>
        Redirect(controllers.portal.routes.Portal.profile())
          .flashing("success" -> Messages("confirmations.profileUpdated"))
      }
    )
  }


  def deleteProfile() = withUserAction { implicit user => implicit request =>
    Ok(p.profile.deleteProfile(deleteForm(user),
      controllers.portal.routes.Portal.deleteProfilePost()))
  }

  def deleteProfilePost() = withUserAction.async { implicit user => implicit request =>
    deleteForm(user).bindFromRequest.fold(
      errForm => immediate(BadRequest(p.profile.deleteProfile(
        errForm.withGlobalError("portal.profile.deleteProfile.badConfirmation"),
        controllers.portal.routes.Portal.deleteProfilePost()))),

      _ => {
        val anonymous = UserProfileF(id = Some(user.id),
          identifier = user.model.identifier, name = user.model.identifier)
        backend.update(user.id, anonymous).flatMap { bool =>
          user.account.get.delete()
          gotoLogoutSucceeded
            .map(_.flashing("success" -> "portal.profile.profileDeleted"))
        }
      }
    )
  }

  def uploadProfileImage = withUserAction { implicit user => implicit request =>
    Ok(p.profile.imageUpload())
  }

  // Body parser that'll refuse anything larger than 5MB
  private def uploadParser = parse.maxLength(5 * 1024 * 1024, parse.multipartFormData)

  def uploadProfileImagePost = withUserAction.async(uploadParser) { implicit user => implicit request =>
    request.body match {
      case Left(MaxSizeExceeded(length)) =>
        immediate(BadRequest(p.profile.imageUpload(Some("portal.error.imageTooLarge"))))
      case Right(multipartForm) => multipartForm.file("image").map { file =>
        if (isValidContentType(file)) {
          convertAndUploadFile(file, user).flatMap { url =>
            backend.patch(user.id, Json.obj(UserProfileF.IMAGE_URL -> url)).map { _ =>
              Redirect(portalRoutes.profile())
            }
          }.recover {
            case S3Exception(status, code, message, originalXml) =>
              Logger.error(s"$originalXml")
              Logger.error("Error: " + message)
              BadRequest(message)
          }
        } else {
          immediate(BadRequest(p.profile.imageUpload(Some("portal.error.badFileType"))))
        }
      }.getOrElse{
        immediate(BadRequest(p.profile.imageUpload(Some("portal.error.noFileGiven"))))
      }
    }
  }

  private def isValidContentType(file: FilePart[TemporaryFile]): Boolean
    = file.contentType.exists(_.toLowerCase.startsWith("image/"))

  private def convertAndUploadFile(file: FilePart[TemporaryFile], user: UserProfile): Future[String] = {
    val bucketName: String = current.configuration.getString("aws.bucket")
      .getOrElse(sys.error("Invalid configuration: no aws.bucket key found"))
    val bucket = S3(bucketName)
    val ctype = file.contentType.getOrElse("application/octet-stream")
    val extension = file.filename.substring(file.filename.lastIndexOf("."))
    val awsName = s"images/${user.id}$extension"
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
