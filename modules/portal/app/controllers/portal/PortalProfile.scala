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
import fly.play.s3.{PUBLIC_READ, BucketFileUploadTicket, BucketFile, S3}
import play.api.libs.iteratee.{Enumeratee, Iteratee}
import play.api.libs.Files.TemporaryFile
import play.api.templates.Html
import play.api.Play._
import scala.Some
import fly.play.s3.BucketFileUploadTicket
import fly.play.s3.BucketFile
import play.api.libs.json.JsObject
import java.io.File
import net.coobird.thumbnailator.Thumbnails

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

  def s3redirect = Action.async { implicit request =>
    println(request.queryString)
    import play.api.data.Form
      import play.api.data.Forms._

    val s3resultForm = Form(
      tuple(
        "bucket" -> nonEmptyText,
        "key" -> nonEmptyText,
        "etag" -> nonEmptyText
      )
    )

    s3resultForm.bindFromRequest.fold(
      errForm => immediate(BadRequest(errForm.errorsAsJson)),
      data => {
        val (bucketName, key, _) = data
        val bucket = S3(bucketName)
        println("URL: " + bucket.url(key))
        bucket.get(key).map {
          case file@BucketFile(name, ctype, content, acl, headers) =>
            Ok(file.toString)
        }
      }
    )

  }

  def uploadProfileForm = withUserAction { implicit user => implicit request =>
    import fly.play.s3.upload.Condition._
    import fly.play.s3.upload.Form
    import java.util.Date
    import fly.play.s3.upload.FormElement
    import play.api.Play.current

    val https = current.configuration.getBoolean("ehri.https")
      .getOrElse(sys.error("Invalid configuration: no ehri.https key found"))

    val timeout = System.currentTimeMillis + (10 * 60 * 1000)
    val bucketName: String = current.configuration.getString("aws.bucket")
      .getOrElse(sys.error("Invalid configuration: no aws.bucket key found"))
    val bucket = S3(bucketName)
    val policy = bucket.uploadPolicy(expiration = new Date(timeout))
        .withConditions(
        key.startsWith(s"images/${user.id}"),
        acl.eq(PUBLIC_READ),
        successActionRedirect.eq(portalRoutes.s3redirect().absoluteURL(https)),
        header(CONTENT_TYPE).startsWith("image/"),
        contentLengthRange.from(0L).to(1024 * 1024), // 1MB
        meta("tag").eq("profileImages"))

    val formFieldsFromPolicy = Form(policy).fields.map {
      case FormElement(name, value, _) =>
        s"""<input type="hidden" name="$name" value="$value" />"""
    }
    val allFormFields =
      formFieldsFromPolicy.mkString("\n") +
        """<input type="file" name="file" accept="image/png,image/jpg" />"""
    println("Form:" + allFormFields)
    Ok(views.html.p.profile.s3uploadForm(bucketName, Html(allFormFields)))
  }


  def uploadProfileImage = withUserAction(parse.multipartFormData) { implicit user => implicit request =>
    request.body.file("image").map { file =>
      val temp = File.createTempFile(user.id, file.filename.substring(file.filename.lastIndexOf(".")))
      val thumb = Thumbnails.of(file.ref.file).size(80,80).toFile(temp)
      ???
    }.getOrElse(BadRequest("no file found"))
  }

  def thumbnailImage(f: File): File = {
    ???
  }
}
