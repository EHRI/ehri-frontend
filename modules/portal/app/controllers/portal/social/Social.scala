package controllers.portal.social

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import models.{SystemEvent, UserProfile}
import views.html.p
import utils._
import utils.search._
import defines.EntityType
import play.api.Play.current
import backend.Backend

import com.google.inject._
import play.api.mvc.RequestHeader
import play.api.i18n.Messages
import play.api.libs.json.Json
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import models.base.AnyModel
import play.api.mvc.Result
import backend.ApiUser
import com.typesafe.plugin.MailerAPI
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 *
 * NB: Things like watching and following items could
 * be greatly optimised by implementing caching for
 * just lists of IDs.
 */
@Singleton
case class Social @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher,
                            searchResolver: Resolver, backend: Backend, accounts: AccountManager,
    mailer: MailerAPI)
  extends PortalController {

  private val socialRoutes = controllers.portal.social.routes.Social

  def browseUsers = WithUserAction.async { implicit request =>
    // This is a bit gnarly because we want to get a searchable list
    // of users and combine it with a list of existing followers so
    // we can mark who's following and who isn't
    val filters = Map(SearchConstants.ACTIVE -> true.toString)
    val defaultParams = SearchParams(entities = List(EntityType.UserProfile),
          sort = Some(SearchOrder.Name), count = Some(40))
    val searchParams = SearchParams.form.bindFromRequest.value
      .getOrElse(defaultParams).setDefault(Some(defaultParams))

    for {
      following <- backend.following[UserProfile](request.user.id, PageParams.empty)
      blocked <- backend.blocked[UserProfile](request.user.id, PageParams.empty)
      srch <- searchDispatcher.setParams(searchParams).withFilters(filters).search()
      users <- searchResolver.resolve[UserProfile](srch.page.items)
    } yield Ok(p.userProfile.browseUsers(
        request.user,
        srch.withItems(users.zip(srch.page.items)),
        controllers.portal.social.routes.Social.browseUsers(),
        following
    ))
  }

  def browseUser(userId: String) = WithUserAction.async { implicit request =>
    // Show the profile home page of a defined user.
    // Activity is the default page
    val listParams = RangeParams.fromRequest(request)
    val eventParams = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    val events: Future[RangePage[SystemEvent]] = backend
      .listEventsByUser[SystemEvent](userId, listParams, eventParams)

    if (isAjax) events.map { theirActivity =>
      Ok(p.activity.eventItems(theirActivity))
        .withHeaders("activity-more" -> theirActivity.more.toString)
    } else {
      val isFollowing: Future[Boolean] = backend.isFollowing(request.user.id, userId)
      val allowMessage: Future[Boolean] = canMessage(request.user.id, userId)
      for {
        them <- backend.get[UserProfile](userId)
        theirActivity <- events
        followed <- isFollowing
        canMessage <- allowMessage
      } yield Ok(p.userProfile.show(them, theirActivity, listParams, followed, canMessage))
    }
  }

  def watchedByUser(userId: String) = WithUserAction.async { implicit request =>
    // Show a list of watched item by a defined User
    val watchParams = PageParams.fromRequest(request, namespace = "w")
    val watching: Future[Page[AnyModel]] = backend.watching[AnyModel](userId, watchParams)
    val isFollowing: Future[Boolean] = backend.isFollowing(request.user.id, userId)
    val allowMessage: Future[Boolean] = canMessage(request.user.id, userId)

    for {
      them <- backend.get[UserProfile](userId)
      theirWatching <- watching
      followed <- isFollowing
      canMessage <- allowMessage
    } yield Ok(p.userProfile.userWatched(them, theirWatching, followed, canMessage))
  }

  def followUser(userId: String) = WithUserAction { implicit request =>
    Ok(p.helpers.simpleForm("social.follow",
      socialRoutes.followUserPost(userId)))
  }

  def followUserPost(userId: String) = WithUserAction.async { implicit request =>
    backend.follow[UserProfile](request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def unfollowUser(userId: String) = WithUserAction { implicit request =>
    Ok(p.helpers.simpleForm("social.unfollow",
      socialRoutes.unfollowUserPost(userId)))
  }

  def unfollowUserPost(userId: String) = WithUserAction.async { implicit request =>
    backend.unfollow[UserProfile](request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def blockUser(userId: String) = WithUserAction { implicit request =>
    Ok(p.helpers.simpleForm("social.block",
      socialRoutes.blockUserPost(userId)))
  }

  def blockUserPost(userId: String) = WithUserAction.async { implicit request =>
    backend.block(request.user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def unblockUser(userId: String) = WithUserAction { implicit request =>
    Ok(p.helpers.simpleForm("social.unblock",
      socialRoutes.unblockUserPost(userId)))
  }

  def unblockUserPost(userId: String) = WithUserAction.async { implicit request =>
    backend.unblock(request.user.id, userId).map { _ =>
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
  def followersForUser(userId: String) = WithUserAction.async { implicit request =>
    val params = PageParams.fromRequest(request)
    val allowMessage: Future[Boolean] = canMessage(request.user.id, userId)
    for {
      them <- backend.get[UserProfile](userId)
      theirFollowers <- backend.followers[UserProfile](userId, params)
      whoImFollowing <- backend.following[UserProfile](request.user.id)
      canMessage <- allowMessage
    } yield {
      if (isAjax)
        Ok(p.userProfile.followerList(them, theirFollowers, params, whoImFollowing))
      else
        Ok(p.userProfile.listFollowers(them, theirFollowers, params, whoImFollowing, canMessage))
    }
  }

  def followingForUser(userId: String) = WithUserAction.async { implicit request =>
    val params = PageParams.fromRequest(request)
    val allowMessage: Future[Boolean] = canMessage(request.user.id, userId)
    for {
      them <- backend.get[UserProfile](userId)
      theirFollowing <- backend.following[UserProfile](userId, params)
      whoImFollowing <- backend.following[UserProfile](request.user.id)
      canMessage <- allowMessage
    } yield {
      if (isAjax)
        Ok(p.userProfile.followingList(them, theirFollowing, params, whoImFollowing))
      else
        Ok(p.userProfile.listFollowing(them, theirFollowing, params, whoImFollowing, canMessage))
    }
  }

  import play.api.data.Form
  import play.api.data.Forms._
  import utils.forms.checkRecapture
  private val messageForm = Form(
    tuple(
      "subject" -> nonEmptyText,
      "message" -> nonEmptyText,
      "copySelf" -> default(boolean, false)
    )
  )

  /**
   * Ascertain if a user can receive messages from other users.
   */
  private def canMessage(senderId: String, recipientId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    // First, find their account. If we don't have
    // an account we don't have an email, so we can't
    // message them... Ignore accounts which have disabled
    // messaging.
    accounts.findById(recipientId).flatMap {
      case Some(account) if account.allowMessaging =>
        backend.isBlocking(recipientId, senderId).map(blocking => !blocking)
      case _ => immediate(false)
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
        mailer
          .setSubject(Messages("mail.message.subject", from.toStringLang, subject))
          .setRecipient(accTo.email)
          .setReplyTo(accFrom.email)
          .setFrom("EHRI User <noreply@ehri-project.eu>")
          .send(views.txt.p.social.mail.messageEmail(heading, subject, message).body,
            views.html.p.social.mail.messageEmail(heading, subject, message).body)

        if (copy) {
          val copyHeading = Messages("mail.message.copy.heading", to.toStringLang)
          mailer
            .setSubject(Messages("mail.message.copy.subject", to.toStringLang, subject))
            .setRecipient(accFrom.email)
            .setFrom("EHRI User <noreply@ehri-project.eu>")
            .send(views.txt.p.social.mail.messageEmail(copyHeading, subject, message, isCopy = true).body,
              views.html.p.social.mail.messageEmail(copyHeading, subject, message, isCopy = true).body)
        }
      }
    }
  }

  def sendMessage(userId: String) = WithUserAction.async { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    for {
      userTo <- backend.get[UserProfile](userId)
      allowed <- canMessage(request.user.id, userId)
    } yield {
      if (allowed) {
        if (isAjax) Ok(p.userProfile.messageForm(userTo, messageForm, socialRoutes.sendMessagePost(userId),
          recaptchaKey))
        else Ok(p.userProfile.messageUser(userTo, messageForm,
          socialRoutes.sendMessagePost(userId), recaptchaKey))
      } else {
        BadRequest(Messages("social.message.send.userNotAcceptingMessages"))
      }
    }
  }

  def sendMessagePost(userId: String) = WithUserAction.async { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    val boundForm = messageForm.bindFromRequest

    def onError(userTo: UserProfile, form: Form[(String,String,Boolean)]): Result = {
      if (isAjax) BadRequest(form.errorsAsJson)
      else BadRequest(p.userProfile.messageUser(userTo, form,
        socialRoutes.sendMessagePost(userId), recaptchaKey))
    }

    def doIt(captcha: Boolean, allowed: Boolean, to: UserProfile): Future[Result] = {
      if (!captcha) {
        immediate(onError(to, boundForm.withGlobalError("error.badRecaptcha")))
      } else if (!allowed) {
        immediate(onError(to, boundForm
          .withGlobalError("social.message.send.userNotAcceptingMessages")))
      } else {
        boundForm.fold(
          errForm => immediate(onError(to, errForm)),
          data => {
            val (subject, message, copyMe) = data
            sendMessageEmail(request.user, to, subject, message, copyMe).map { _ =>
              val msg = Messages("social.message.send.confirmation")
              if (isAjax) Ok(Json.obj("ok" -> msg))
              else Redirect(socialRoutes.browseUser(userId))
                .flashing("success" -> msg)
            }
          }
        )
      }
    }

    for {
      captcha <- checkRecapture
      allowed <- canMessage(request.user.id, userId)
      userTo <- backend.get[UserProfile](userId)
      r <- doIt(captcha, allowed, userTo)
    } yield r
  }
}
