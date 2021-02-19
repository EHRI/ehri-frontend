package controllers.portal.users

import akka.stream.Materializer
import controllers.generic.Search
import controllers.portal.base.PortalController
import controllers.{AppComponents, DataFormat}
import models.{Model, _}
import models.view.MessagingInfo
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import play.api.Logger
import play.api.http.{HeaderNames, MimeTypes}
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.libs.mailer.MailerClient
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MaxSizeExceeded, _}
import services.search._
import services.storage.FileStorage
import utils._

import java.io.File
import javax.inject._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class UserProfiles @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  mailer: MailerClient,
  fileStorage: FileStorage
) extends PortalController
  with Search
  with CsvHelpers {

  private implicit val mat: Materializer = appComponents.materializer
  private val logger = Logger(classOf[UserProfiles])

  private val profileRoutes = controllers.portal.users.routes.UserProfiles

  def watchItem(id: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.helpers.simpleForm("profile.watch",
      profileRoutes.watchItemPost(id)))
  }

  def watchItemPost(id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.watch(request.user.id, id).map { _ =>
      clearWatchedItemsCache(request.user.id)
      if (isAjax) Ok("ok")
      else Redirect(profileRoutes.watching())
    }
  }

  def unwatchItem(id: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.helpers.simpleForm("profile.unwatch",
      profileRoutes.unwatchItemPost(id)))
  }

  def unwatchItemPost(id: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.unwatch(request.user.id, id).map { _ =>
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
    def fromItem(item: Model)(implicit request: RequestHeader): ExportWatchItem = new ExportWatchItem(
      item.toStringLang,
      views.Helpers.linkTo(item).absoluteURL(conf.https)
    )
    implicit val writes: OWrites[ExportWatchItem] = Json.writes[ExportWatchItem]
  }

  def activity(params: SystemEventParams, range: RangeParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    // Show the profile home page of a defined user.
    // Activity is the default page
    val eventParams: SystemEventParams = params
      .copy(eventTypes = activityEventTypes, itemTypes = activityItemTypes)
    val eventsF: Future[RangePage[Seq[SystemEvent]]] =
      userDataApi.userActions[SystemEvent](request.user.id, range, eventParams)
    val messagingInfoF = getMessagingInfo(request.user.id, request.user.id)

    for (myActivity <- eventsF; messagingInfo <- messagingInfoF) yield {
      if (isAjax) Ok(views.html.activity.eventItems(myActivity))
        .withHeaders("activity-more" -> myActivity.more.toString)
      else Ok(views.html.userProfile.show(request.user, myActivity,
        range, eventParams, followed = false, messagingInfo = messagingInfo))
    }
  }

  def watching(format: DataFormat.Value = DataFormat.Html, params: SearchParams, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    for {
      watching <- userDataApi.watching[Model](request.user.id)
      result <- findIn[Model](watching, params, paging)
      messagingInfo <- getMessagingInfo(request.user.id, request.user.id)
    } yield {
      val watchList = result.mapItems(_._1).page
      format match {
        case DataFormat.Text => Ok(views.txt.userProfile.watchedItems(watchList))
          .as(MimeTypes.TEXT)
        case DataFormat.Csv | DataFormat.Tsv => Ok.chunked(writeCsv(
          List("Item", "URL"),
          watchList.items.map(a => ExportWatchItem.fromItem(a).toCsv),
            sep = if (format == DataFormat.Csv) ',' else '\t'))
          .as(s"text/$format; charset=utf-8")
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=${request.user.id}_watched.$format")
        case DataFormat.Json =>
          Ok(Json.toJson(watchList.items.map(ExportWatchItem.fromItem)))
            .as(MimeTypes.JSON)
        case DataFormat.Html => Ok(views.html.userProfile.watched(
          request.user,
          result,
          searchAction = profileRoutes.watching(format = DataFormat.Html),
          followed = false,
          messagingInfo,
          watching.map(_.id) // our current watched item IDs
        ))
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
      annotation.data.field,
      annotation.data.body,
      annotation.latestEvent.map(_.time),
      annotation.target
        .map(t => views.Helpers.linkTo(t).absoluteURL(conf.https) + "#" + annotation.id)
    )
    import play.api.libs.json._
    implicit val writes: OWrites[ExportAnnotation] = Json.writes[ExportAnnotation]
  }

  def annotations(format: DataFormat.Value = DataFormat.Html, params: SearchParams, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    findType[Annotation](params, paging, filters = Map(SearchConstants.ANNOTATOR_ID -> request.user.id)).map { result =>
      val itemsOnly = result.mapItems(_._1).page
      format match {
        case DataFormat.Text =>
          Ok(views.txt.userProfile.annotations(itemsOnly).body.trim)
            .as(MimeTypes.TEXT)
        case DataFormat.Csv | DataFormat.Tsv => Ok.chunked(writeCsv(
            List("Item", "Field", "Note", "Time", "URL"),
            itemsOnly.items.map(a => ExportAnnotation.fromAnnotation(a).toCsv),
              sep = if (format == DataFormat.Csv) ',' else '\t'))
          .as(s"text/$format; charset=utf-8")
          .withHeaders(HeaderNames.CONTENT_DISPOSITION -> s"attachment; filename=${request.user.id}_notes.$format")
        case DataFormat.Json =>
          Ok(Json.toJson(itemsOnly.items.map(ExportAnnotation.fromAnnotation)))
            .as(MimeTypes.JSON)
        case _ => Ok(views.html.userProfile.annotations(
          request.user,
          annotations = result,
          searchAction = profileRoutes.profile(),
          followed = false,
          messagingInfo = MessagingInfo(request.user.id)
         )
        )
      }
    }
  }

  def annotationListToJson(annotations: Seq[Annotation])(implicit request: RequestHeader): JsValue
    = Json.toJson(annotations.map(ExportAnnotation.fromAnnotation))

  // For now the user's profile main page is just their notes.
  def profile(params: SearchParams, paging: PageParams): Action[AnyContent] = annotations(format = DataFormat.Html, params, paging)

  import play.api.data.Form
  import play.api.data.Forms._
  private def deleteForm(user: UserProfile): Form[String] = Form(
    single(
      "confirm" -> nonEmptyText.verifying("profile.delete.badConfirmation",
        name => user.data.name.trim == name.trim
      )
    )
  )

  private val imageForm = Form(
    single("image" -> text)
  )

  private def profileDataForm(implicit userOpt: Option[UserProfile]): Form[ProfileData] = {
    userOpt.map { user =>
      ProfileData.form.fill(ProfileData.fromUser(user.data))
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

  def updateAccountPrefsPost(): Action[AnyContent] = WithUserAction.async { implicit request =>
    AccountPreferences.form.bindFromRequest().fold(
      errForm => immediate(BadRequest(views.html.userProfile.editProfile(
            ProfileData.form, imageForm, errForm))),
      accountPrefs => accounts.findById(request.user.id).flatMap {
        case Some(account) =>
          accounts.update(account.copy(allowMessaging = accountPrefs.allowMessaging)).map { _ =>
            Redirect(profileRoutes.updateProfile())
              .flashing("success" -> "profile.preferences.updated")
          }
        case _ => authenticationFailed(request)
      }
    )
  }

  def updateProfile(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.userProfile.editProfile(profileDataForm, imageForm, accountPrefsForm))
  }

  def updateProfilePost(): Action[AnyContent] = WithUserAction.async { implicit request =>
    ProfileData.form.bindFromRequest().fold(
      errForm => immediate(
        BadRequest(views.html.userProfile.editProfile(errForm, imageForm, accountPrefsForm))
      ),
      profile => userDataApi.update[UserProfile, UserProfileF](
          request.user.id, profile.toUser(request.user.data)).map { _ =>
        Redirect(profileRoutes.profile())
          .flashing("success" -> Messages("profile.update.confirmation"))
      }
    )
  }

  def deleteProfile(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.userProfile.deleteProfile(deleteForm(request.user),
      profileRoutes.deleteProfilePost()))
  }

  def deleteProfilePost(): Action[AnyContent] = WithUserAction.async { implicit request =>
    deleteForm(request.user).bindFromRequest().fold(
      errForm => immediate(BadRequest(views.html.userProfile.deleteProfile(
        errForm.withGlobalError("profile.delete.badConfirmation"),
        profileRoutes.deleteProfilePost()))),
      _ => {
        // Here we are not going to delete their whole profile, since
        // that would destroy the record of the user's activities and
        // the provenance of the data. Instead we just anonymize it by
        // updating the record with minimal information
        val anonProfile = UserProfileF(id = Some(request.user.id),
          identifier = request.user.data.identifier, name = request.user.data.identifier,
          active = false)

        userDataApi.update[UserProfile,UserProfileF](request.user.id, anonProfile).flatMap { bool =>
          accounts.delete(request.user.id).flatMap { _ =>
            gotoLogoutSucceeded
              .map(_.flashing("success" -> "profile.profile.delete.confirmation"))
          }
        }
      }
    )
  }

  // Defer to the standard profile update page...
  def updateProfileImage(): Action[AnyContent] = updateProfile()

  // Body parser that'll refuse anything larger than maxImageSize
  private def uploadParser = parsers.maxLength(
    config.underlying.getBytes("ehri.portal.profile.maxImageSize"), parsers.multipartFormData)

  def updateProfileImagePost(): Action[Either[MaxSizeExceeded, MultipartFormData[TemporaryFile]]] = WithUserAction.async(uploadParser) { implicit request =>

    def onError(err: String, status: Status = BadRequest): Future[Result] = immediate(
        status(views.html.userProfile.editProfile(profileDataForm,
          imageForm.withGlobalError(err), accountPrefsForm)))

    request.body match {
      case Left(MaxSizeExceeded(size)) =>
        logger.debug(s"Profile image upload size too large: $size")
        onError("errors.imageTooLarge", EntityTooLarge)
      case Right(multipartForm) => multipartForm.file("image").map { file =>
        if (isValidContentType(file)) {
          try {
            for {
              url <- convertAndUploadFile(file, request.user, request)
              _ <- userDataApi.patch[UserProfile](request.user.id, Json.obj(UserProfileF.IMAGE_URL -> url))
            } yield Redirect(profileRoutes.profile())
                  .flashing("success" -> "profile.update.confirmation")
          } catch {
            case _: UnsupportedFormatException => onError("errors.badFileType")
          }
        } else {
          onError("errors.badFileType")
        }
      }.getOrElse {
        onError("errors.noFileGiven")
      }
    }
  }

  private def isValidContentType(file: FilePart[TemporaryFile]): Boolean =
    file.contentType.exists(_.toLowerCase.startsWith("image/"))

  private def convertAndUploadFile(file: FilePart[TemporaryFile], user: UserProfile, request: RequestHeader): Future[String] = {
    val instance = config.getOptional[String]("storage.instance").getOrElse(request.host)
    val extension = file.filename.substring(file.filename.lastIndexOf(".")).toLowerCase
    val storeName = s"$instance/images/${user.isA}/${user.id}$extension"
    val temp = File.createTempFile(user.id, extension)
    Thumbnails.of(file.ref.path.toFile).size(200, 200).toFile(temp)
    val url: Future[String] = fileStorage.putFile(storeName, temp, public = true).map(_.toString)
    url.onComplete { _ => temp.delete() }
    url
  }
}
