package controllers.portal.social

import controllers.AppComponents
import controllers.base.RecaptchaHelpers
import controllers.generic.Search
import controllers.portal.base.PortalController

import javax.inject._
import models.view.MessagingInfo
import models.{Model, SystemEvent, UserProfile}
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.libs.mailer.{Email, MailerClient}
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.cypher.CypherService
import services.search._
import utils._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

@Singleton
case class Social @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  mailer: MailerClient,
  ws: WSClient,
  cypher: CypherService
) extends PortalController
  with RecaptchaHelpers
  with Search {

  // NB: Things like watching and following items could
  // be greatly optimised by implementing caching for
  // just lists of IDs.

  private val socialRoutes = controllers.portal.social.routes.Social

  private val usersPerPage = 18

  // Profile is currently an alias for the watch list
  def userProfile(userId: String, params: SearchParams, paging: PageParams): Action[AnyContent] =
    userWatchList(userId, params, paging)

  def browseUsers(params: SearchParams, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    // This is a bit gnarly because we want to get a searchable list
    // of users and combine it with a list of existing followers so
    // we can mark who's following and who isn't
    for {
      following <- userDataApi.following[UserProfile](request.user.id, PageParams.empty)
      _ <- userDataApi.blocked[UserProfile](request.user.id, PageParams.empty)
      result <- findType[UserProfile](params, paging.copy(limit = usersPerPage),
        filters = Map(SearchConstants.ACTIVE -> true.toString))
    } yield {
      if (isAjax) Ok(views.html.userProfile.browseUsersList(
        result,
        controllers.portal.social.routes.Social.browseUsers(),
        following)).withHeaders("more" -> result.page.hasMore.toString)
      else Ok(views.html.userProfile.browseUsers(
        request.user,
        result,
        controllers.portal.social.routes.Social.browseUsers(),
        following))
    }
  }

  def userActivity(userId: String, params: SystemEventParams, range: RangeParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    // Show the profile home page of a defined user.
    // Activity is the default page
    val eventParams = params
      .copy(eventTypes = activityEventTypes, itemTypes = activityItemTypes)
    val events: Future[RangePage[Seq[SystemEvent]]] =
      userDataApi.userActions[SystemEvent](userId, range, eventParams)

    if (isAjax) events.map { theirActivity =>
      Ok(views.html.activity.eventItems(theirActivity))
        .withHeaders("activity-more" -> theirActivity.more.toString)
    } else {
      val isFollowing: Future[Boolean] = userDataApi.isFollowing(request.user.id, userId)
      val messagingInfoF: Future[MessagingInfo] = getMessagingInfo(request.user.id, userId)
      for {
        them <- userDataApi.get[UserProfile](userId)
        theirActivity <- events
        followed <- isFollowing
        messagingInfo <- messagingInfoF
      } yield Ok(views.html.userProfile.show(
          them, theirActivity, range, eventParams, followed, messagingInfo))
    }
  }

  def userWatchList(userId: String, params: SearchParams, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    // Show a list of watched item by a defined User
    val themF = userDataApi.get[UserProfile](userId)
    val theirWatchingF: Future[Page[Model]] = userDataApi.watching[Model](userId)
    val myWatchingF: Future[Seq[String]] = watchedItemIds(Some(request.user.id))
    val isFollowingF: Future[Boolean] = userDataApi.isFollowing(request.user.id, userId)
    val messagingInfoF: Future[MessagingInfo] = getMessagingInfo(request.user.id, userId)

    for {
      them <- themF
      followed <- isFollowingF
      messagingInfo <- messagingInfoF
      theirWatching <- theirWatchingF
      myWatching <- myWatchingF
      result <- findIn[Model](theirWatching, params, paging)
    } yield Ok(views.html.userProfile.watched(
      them,
      result,
      socialRoutes.userWatchList(userId),
      followed,
      messagingInfo,
      myWatching
    ))
  }

  def followUser(userId: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.helpers.simpleForm("social.follow",
      socialRoutes.followUserPost(userId)))
  }

  def followUserPost(userId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.follow[UserProfile](request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def unfollowUser(userId: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.helpers.simpleForm("social.unfollow",
      socialRoutes.unfollowUserPost(userId)))
  }

  def unfollowUserPost(userId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.unfollow[UserProfile](request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def blockUser(userId: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.helpers.simpleForm("social.block",
      socialRoutes.blockUserPost(userId)))
  }

  def blockUserPost(userId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.block(request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def unblockUser(userId: String): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.helpers.simpleForm("social.unblock",
      socialRoutes.unblockUserPost(userId)))
  }

  def unblockUserPost(userId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.unblock(request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  /**
   * Render list of someone else's followers via Ajax...
   */
  def followersForUser(userId: String, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    val messagingInfoF: Future[MessagingInfo] = getMessagingInfo(request.user.id, userId)
    for {
      them <- userDataApi.get[UserProfile](userId)
      theirFollowers <- userDataApi.followers[UserProfile](userId, paging)
      whoImFollowing <- userDataApi.following[UserProfile](request.user.id, PageParams.empty.withoutLimit)
      messagingInfo <- messagingInfoF
    } yield {
      if (isAjax)
        Ok(views.html.userProfile.followerList(them, theirFollowers, paging, whoImFollowing))
      else
        Ok(views.html.userProfile.listFollowers(them, theirFollowers, paging, whoImFollowing, messagingInfo))
    }
  }

  def followingForUser(userId: String, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    val messagingInfoF: Future[MessagingInfo] = getMessagingInfo(request.user.id, userId)
    val themF = userDataApi.get[UserProfile](userId)
    val followingF = userDataApi.following[UserProfile](userId, paging)
    val whoImFollowingF = userDataApi.following[UserProfile](request.user.id, PageParams.empty.withoutLimit)
    for (them <- themF; following <- followingF; whoImFollowing <- whoImFollowingF; messagingInfo <- messagingInfoF) yield {
      if (isAjax)
        Ok(views.html.userProfile.followingList(them, following, paging, whoImFollowing))
      else
        Ok(views.html.userProfile.listFollowing(them, following, paging, whoImFollowing, messagingInfo))
    }
  }


  private def sendMessageEmail(from: UserProfile, to: UserProfile, subject: String, message: String, copy: Boolean)(implicit request: RequestHeader): Future[Unit] = {
    for {
      accFromOpt <- accounts.findById(from.id)
      accToOpt <- accounts.findById(to.id)
    } yield {
      for {
        accFrom <- accFromOpt
        accTo <- accToOpt
      } yield {
        val heading = Messages("mail.message.heading", from.toStringLang)
        val emailMessage = Email(
          subject = Messages("mail.message.heading", from.toStringLang),
          to = Seq(s"${to.data.name}} <${accTo.email}>"),
          from = s"EHRI User <${config.get[String]("ehri.portal.emails.messages")}>",
          replyTo = Seq(s"${from.data.name}} <${accFrom.email}>"),
          bodyText = Some(views.txt.social.mail.messageEmail(heading, subject, message).body),
          bodyHtml = Some(views.html.social.mail.messageEmail(heading, subject, message).body)
        )
        mailer.send(emailMessage)

        if (copy) {
          val copyHeading = Messages("mail.message.copy.heading", to.toStringLang)
          val copyEmailMessage = emailMessage.copy(
            subject = Messages("mail.message.copy.heading", to.toStringLang),
            to = Seq(s"${from.data.name} <${accFrom.email}>"),
            bodyText = Some(views.txt.social.mail.messageEmail(copyHeading, subject, message, isCopy = true).body),
            bodyHtml = Some(views.html.social.mail.messageEmail(copyHeading, subject, message, isCopy = true).body)
          )
          mailer.send(copyEmailMessage)
        }
      }
    }
  }

  def sendMessage(userId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    for {
      userTo <- userDataApi.get[UserProfile](userId)
      info <- getMessagingInfo(request.user.id, userId)
    } yield {
      if (info.canMessage) {
        if (isAjax) Ok(views.html.userProfile.messageForm(userTo, info))
        else Ok(views.html.userProfile.messageUser(userTo, info))
      } else {
        BadRequest(Messages("social.message.send.userNotAcceptingMessages"))
      }
    }
  }

  def sendMessagePost(userId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    val boundForm = messageForm.bindFromRequest()

    def onError(userTo: UserProfile, info: MessagingInfo): Result = {
      if (isAjax) BadRequest(info.form.errorsAsJson)
      else BadRequest(views.html.userProfile.messageUser(userTo, info))
    }

    def doIt(captcha: Boolean, info: MessagingInfo, to: UserProfile): Future[Result] = {
      if (!captcha) {
        immediate(onError(to, info.copy(form = boundForm.withGlobalError("error.badRecaptcha"))))
      } else if (!info.canMessage) {
        immediate(onError(to, info.copy(form = boundForm
          .withGlobalError("social.message.send.userNotAcceptingMessages"))))
      } else {
        boundForm.fold(
          errForm => immediate(onError(to, info.copy(form = errForm))),
          data => {
            val (subject, message, copyMe) = data
            sendMessageEmail(request.user, to, subject, message, copyMe).map { _ =>
              val msg = Messages("social.message.send.confirmation")
              if (isAjax) Ok(Json.obj("ok" -> msg))
              else Redirect(socialRoutes.userProfile(userId)).flashing("success" -> msg)
            }
          }
        )
      }
    }

    for {
      captcha <- checkRecapture
      info <- getMessagingInfo(request.user.id, userId)
      userTo <- userDataApi.get[UserProfile](userId)
      r <- doIt(captcha, info, userTo)
    } yield r
  }
}
