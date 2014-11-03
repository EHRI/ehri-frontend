package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.{SystemEvent, UserProfile, AccountDAO}
import views.html.p
import utils._
import utils.search.{Resolver, SearchOrder, Dispatcher, SearchParams}
import defines.{EventType, EntityType}
import play.api.Play.current
import solr.SolrConstants
import backend.Backend

import com.google.inject._
import play.api.mvc.RequestHeader
import play.api.i18n.Messages
import play.api.libs.json.Json
import scala.concurrent.Future
import models.base.AnyModel
import backend.rest.Constants
import scala.Some
import play.api.mvc.Result
import backend.ApiUser
import com.typesafe.plugin.MailerAPI

/**
 * @author Mike Bryant (http://github.com/mikesname)
 *
 * NB: Things like watching and following items could
 * be greatly optimised by implementing caching for
 * just lists of IDs.
 */
case class Social @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO,
    mailer: MailerAPI)
  extends AuthController
  with ControllerHelpers
  with PortalAuthConfigImpl
  with SessionPreferences[SessionPrefs] {

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  val defaultPreferences = new SessionPrefs

  val activityEventTypes = List(
    EventType.deletion,
    EventType.creation,
    EventType.modification,
    EventType.modifyDependent,
    EventType.createDependent,
    EventType.deleteDependent,
    EventType.link,
    EventType.annotation
  )

  val activityItemTypes = List(
    EntityType.DocumentaryUnit,
    EntityType.Repository,
    EntityType.Country,
    EntityType.HistoricalAgent,
    EntityType.Link,
    EntityType.Annotation
  )

  private val socialRoutes = controllers.portal.routes.Social

  def personalisedActivity(offset: Int = 0, limit: Int = Constants.DEFAULT_LIST_LIMIT) = {
    withUserAction.async { implicit user => implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
      val listParams = RangeParams.fromRequest(request).copy(offset = offset)
      val incParams = listParams.copy(limit = listParams.limit + 1)
      val eventFilter = SystemEventParams.fromRequest(request)
        .copy(eventTypes = activityEventTypes)
        .copy(itemTypes = activityItemTypes)
      backend.listEventsForUser[SystemEvent](user.id, incParams, eventFilter).map { events =>
        val more = events.size > listParams.limit

        val displayEvents = events.take(listParams.limit)
        if (isAjax) Ok(p.activity.eventItems(displayEvents))
          .withHeaders("activity-more" -> more.toString)
        else Ok(p.activity.activity(displayEvents, listParams, more))
      }
    }
  }

  def browseUsers = withUserAction.async { implicit user => implicit request =>
    // This is a bit gnarly because we want to get a searchable list
    // of users and combine it with a list of existing followers so
    // we can mark who's following and who isn't
    val filters = Map(SolrConstants.ACTIVE -> true.toString)
    val defaultParams = SearchParams(entities = List(EntityType.UserProfile),
          sort = Some(SearchOrder.Name), count = Some(40))
    val searchParams = SearchParams.form.bindFromRequest.value
      .getOrElse(defaultParams).setDefault(Some(defaultParams))

    for {
      following <- backend.following[UserProfile](user.id, PageParams.empty)
      blocked <- backend.blocked[UserProfile](user.id, PageParams.empty)
      srch <- searchDispatcher.search(searchParams, Nil, Nil, filters)
      users <- searchResolver.resolve[UserProfile](srch.items)
    } yield Ok(p.social.browseUsers(user, srch.copy(items = users), searchParams, following))
  }

  def browseUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    // Show the profile home page of a defined user.
    // Activity is the default page
    val listParams = RangeParams.fromRequest(request)
    val incParams = listParams.copy(limit = listParams.limit + 1)
    val eventParams = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    val events: Future[(Boolean, Seq[SystemEvent])] = backend
      .listEventsByUser[SystemEvent](userId, incParams, eventParams).map { events =>
      val more = events.size > listParams.limit
      (more, events.take(listParams.limit))
    }
    val isFollowing: Future[Boolean] = backend.isFollowing(user.id, userId)
    val allowMessage: Future[Boolean] = canMessage(user.id, userId)

    for {
      them <- backend.get[UserProfile](userId)
      (more, theirActivity) <- events
      followed <- isFollowing
      canMessage <- allowMessage
    } yield Ok(p.social.browseUser(them, theirActivity, more, listParams, followed, canMessage))
  }

  def moreUserActivity(userId: String, offset: Int = 0, limit: Int = Constants.DEFAULT_LIST_LIMIT) = {
    userProfileAction.async { implicit userOpt => implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
      val listParams = RangeParams.fromRequest(request).copy(offset = offset)
      val incParams = listParams.copy(limit = listParams.limit + 1)
      val eventFilter = SystemEventParams.fromRequest(request)
        .copy(eventTypes = activityEventTypes)
        .copy(itemTypes = activityItemTypes)
      backend.listEventsByUser[SystemEvent](userId, incParams, eventFilter).map { events =>
        val more = events.size > listParams.limit
        val displayEvents = events.take(listParams.limit)
        Ok(p.activity.eventItems(displayEvents))
          .withHeaders("activity-more" -> more.toString)
      }
    }
  }

  def watchedByUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    // Show a list of watched item by a defined User
    val watchParams = PageParams.fromRequest(request, namespace = "w")
    val watching: Future[Page[AnyModel]] = backend.watching[AnyModel](userId, watchParams)
    val isFollowing: Future[Boolean] = backend.isFollowing(user.id, userId)
    val allowMessage: Future[Boolean] = canMessage(user.id, userId)

    for {
      them <- backend.get[UserProfile](userId)
      theirWatching <- watching
      followed <- isFollowing
      canMessage <- allowMessage
    } yield Ok(p.social.userWatched(them, theirWatching, followed, canMessage))
  }

  def followUser(userId: String) = withUserAction { implicit user => implicit request =>
    Ok(p.helpers.simpleForm("portal.social.follow",
      socialRoutes.followUserPost(userId)))
  }

  def followUserPost(userId: String) = withUserAction.async { implicit user => implicit request =>
    backend.follow(user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(controllers.portal.routes.Social.browseUsers())
      }
    }
  }

  def unfollowUser(userId: String) = withUserAction { implicit user => implicit request =>
    Ok(p.helpers.simpleForm("portal.social.unfollow",
      socialRoutes.unfollowUserPost(userId)))
  }

  def unfollowUserPost(userId: String) = withUserAction.async { implicit user => implicit request =>
    backend.unfollow(user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def blockUser(userId: String) = withUserAction { implicit user => implicit request =>
    Ok(p.helpers.simpleForm("portal.social.block",
      socialRoutes.blockUserPost(userId)))
  }

  def blockUserPost(userId: String) = withUserAction.async { implicit user => implicit request =>
    backend.block(user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(controllers.portal.routes.Social.browseUsers())
      }
    }
  }

  def unblockUser(userId: String) = withUserAction { implicit user => implicit request =>
    Ok(p.helpers.simpleForm("portal.social.unblock",
      socialRoutes.unblockUserPost(userId)))
  }

  def unblockUserPost(userId: String) = withUserAction.async { implicit user => implicit request =>
    backend.unblock(user.id, userId).map { _ =>
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
  def followersForUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    val params = PageParams.fromRequest(request)
    for {
      them <- backend.get[UserProfile](userId)
      theirFollowers <- backend.followers[UserProfile](userId, params)
      whoImFollowing <- backend.following[UserProfile](user.id)
    } yield {
      if (isAjax)
        Ok(p.social.followerList(them, theirFollowers, params, whoImFollowing))
      else
        Ok(p.social.listFollowers(them, theirFollowers, params, whoImFollowing))
    }
  }

  def followingForUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    val params = PageParams.fromRequest(request)
    for {
      them <- backend.get[UserProfile](userId)
      theirFollowing <- backend.following[UserProfile](userId, params)
      whoImFollowing <- backend.following[UserProfile](user.id)
    } yield {
      if (isAjax)
        Ok(p.social.followingList(them, theirFollowing, params, whoImFollowing))
      else
        Ok(p.social.listFollowing(them, theirFollowing, params, whoImFollowing))
    }
  }

  import play.api.data.Form
  import play.api.data.Forms._
  import utils.forms.checkRecapture
  private val messageForm = Form(
    tuple(
      "subject" -> nonEmptyText,
      "message" -> nonEmptyText
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
    userDAO.findByProfileId(recipientId).filter(_.allowMessaging).map { account =>
      // If the recipient is blocking the sender they can't send
      // a message.
      backend.isBlocking(recipientId, senderId).map(blocking => !blocking)
    } getOrElse {
      Future.successful(false)
    }
  }

  private def sendMessageEmail(from: UserProfile, to: UserProfile, subject: String, message: String)(implicit request: RequestHeader): Unit = {
    for {
      accFrom <- userDAO.findByProfileId(from.id)
      accTo <- userDAO.findByProfileId(to.id)
    } yield {
      mailer
        .setSubject(Messages("portal.mail.message.subject", from.toStringLang, subject))
        .setRecipient(accTo.email)
        .setReplyTo(accFrom.email)
        .setFrom("EHRI User <noreply@ehri-project.eu>")
        .send(views.txt.p.social.mail.messageEmail(from, subject, message).body,
        views.html.p.social.mail.messageEmail(from, subject, message).body)
    }
  }

  def sendMessage(userId: String) = withUserAction.async { implicit user => implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    for {
      userTo <- backend.get[UserProfile](userId)
      allowed <- canMessage(user.id, userId)
    } yield {
      if (allowed) {
        if (isAjax) Ok(p.social.messageForm(userTo, messageForm, socialRoutes.sendMessagePost(userId),
          recaptchaKey))
        else Ok(p.social.messageUser(userTo, messageForm,
          socialRoutes.sendMessagePost(userId), recaptchaKey))
      } else {
        BadRequest(Messages("portal.social.message.send.userNotAcceptingMessages"))
      }
    }
  }

  def sendMessagePost(userId: String) = withUserAction.async { implicit user => implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    val boundForm = messageForm.bindFromRequest

    def onError(userTo: UserProfile, form: Form[(String,String)]): Result = {
      if (isAjax) BadRequest(form.errorsAsJson)
      else BadRequest(p.social.messageUser(userTo, form,
        socialRoutes.sendMessagePost(userId), recaptchaKey))
    }

    for {
      captcha <- checkRecapture
      allowed <- canMessage(user.id, userId)
      userTo <- backend.get[UserProfile](userId) if allowed
    } yield {
      if (!captcha) {
        onError(userTo, boundForm.withGlobalError("error.badRecaptcha"))
      } else if (!allowed) {
        onError(userTo, boundForm
          .withGlobalError("portal.social.message.send.userNotAcceptingMessages"))
      } else {
        boundForm.fold(
          errForm => onError(userTo, errForm),
          data => {
            val (subject, message) = data
            sendMessageEmail(user, userTo, subject, message)
            val msg = Messages("portal.social.message.send.confirmation")
            if (isAjax) Ok(Json.obj("ok" -> msg))
            else Redirect(socialRoutes.browseUser(userId))
              .flashing("success" -> msg)
          }
        )
      }
    }
  }
}
