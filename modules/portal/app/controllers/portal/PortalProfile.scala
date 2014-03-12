package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.{ProfileData, UserProfile, UserProfileF}
import play.api.i18n.Messages
import play.api.mvc._
import defines.{ContentTypes, EntityType}
import play.api.libs.json.{JsObject, Json}
import utils.{SessionPrefs, PageParams}
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import fly.play.s3.{BucketFileUploadTicket, BucketFile, S3}
import play.api.libs.iteratee.{Enumeratee, Iteratee}
import play.api.libs.Files.TemporaryFile

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
    } yield Ok(views.html.p.profile.profile(watchList, anns, links))
  }

  def watching = withUserAction.async { implicit user => implicit request =>
    val watchParams = PageParams.fromRequest(request)
    backend.pageWatching(user.id, watchParams).map { watchList =>
      Ok(views.html.p.profile.watchedItems(watchList))
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
   * @return
   */
  def changePasswordPost = changePasswordPostAction { boolOrErr => implicit userOpt => implicit request =>
    boolOrErr match {
      case Right(true) =>
        Redirect(defaultLoginUrl)
          .flashing("success" -> Messages("login.passwordChanged"))
      case Right(false) =>
        BadRequest(views.html.p.profile.editProfile(
          ProfileData.form, changePasswordForm
            .withGlobalError("login.badUsernameOrPassword")))
      case Left(errForm) =>
        BadRequest(views.html.p.profile.editProfile(
          ProfileData.form, errForm))
    }
  }

  def updateProfile() = withUserAction { implicit user => implicit request =>
    val form = ProfileData.form.fill(ProfileData.fromUser(user))
      Ok(views.html.p.profile.editProfile(
        form, changePasswordForm))
  }

  def updateProfilePost() = withUserAction.async { implicit user => implicit request =>
    ProfileData.form.bindFromRequest.fold(
      errForm => immediate(BadRequest(views.html.p.profile.editProfile(
        errForm, changePasswordForm))),
      profile => backend.patch[UserProfile](user.id, Json.toJson(profile).as[JsObject]).map { userProfile =>
        Redirect(controllers.portal.routes.Portal.profile())
          .flashing("success" -> Messages("confirmations.profileUpdated"))
      }
    )
  }


  def deleteProfile() = withUserAction { implicit user => implicit request =>
    Ok(views.html.p.profile.deleteProfile(deleteForm(user),
      controllers.portal.routes.Portal.deleteProfilePost()))
  }

  def deleteProfilePost() = withUserAction.async { implicit user => implicit request =>
    deleteForm(user).bindFromRequest.fold(
      errForm => immediate(BadRequest(views.html.p.profile.deleteProfile(
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

  object UploadHandler {
    def upload(bucket: S3, ticket: BucketFileUploadTicket) = {
      val consumeAMB = play.api.libs.iteratee.Traversable.takeUpTo[Array[Byte]](1028*1028) &>> Iteratee.consume()

      val rechunkAdapter:Enumeratee[Array[Byte],Array[Byte]] = Enumeratee.grouped(consumeAMB)

      val writeToStore: Iteratee[Array[Byte],BucketFileUploadTicket] =
        Iteratee.foldM[Array[Byte],BucketFileUploadTicket](ticket) { (c,bytes) =>

          // write bytes and return next handle, probable in a Future
          ???
        }

      rechunkAdapter &>> writeToStore
    }

    def s3PartHandler(bucket: S3, ticket: BucketFileUploadTicket): BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[BucketFileUploadTicket]] = {
      parse.Multipart.handleFilePart {
        case parse.Multipart.FileInfo(partName, filename, ct) => upload(bucket, ticket)
      }
    }
  }


  def uploadProfileImage = withUserAction.async(parse.multipartFormData) { implicit user => implicit request =>
    request.body.file("image").map { picture =>
      val s3 = S3("ehri-users")
      val bucketFile = new BucketFile(user.id + "_" + picture.filename,
        picture.contentType.getOrElse(play.api.http.ContentTypes.BINARY))

      for {
        ticket <- s3.initiateMultipartUpload(bucketFile)


      } yield ???
    }.getOrElse(immediate(BadRequest("no image found")))
  }
}
