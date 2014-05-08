package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.UserProfile
import views.html.p
import utils.{SessionPrefs, PageParams, SystemEventParams, ListParams}
import utils.search.{Resolver, SearchOrder, Dispatcher, SearchParams}
import defines.{EventType, EntityType}
import play.api.Play.current
import solr.SolrConstants
import models.AccountDAO
import backend.{ApiUser, Backend}

import com.google.inject._
import play.api.mvc.{Result, RequestHeader}
import play.api.i18n.Messages
import play.api.libs.json.Json
import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 *
 * NB: Things like watching and following items could
 * be greatly optimised by implementing caching for
 * just lists of IDs.
 */
case class Social @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO)
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

  def personalisedActivity(offset: Int = 0) = withUserAction.async { implicit user => implicit request =>
    // NB: Increasing the limit by 1 over the default so we can
    // detect if there are additional items to display
    val listParams = ListParams.fromRequest(request).copy(offset = offset)
    val incParams = listParams.copy(limit = listParams.limit + 1)
    val eventFilter = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    backend.listEventsForUser(user.id, incParams, eventFilter).map { events =>
      val more = events.size > listParams.limit

      val displayEvents = events.take(listParams.limit)
      if (isAjax) Ok(p.activity.eventItems(displayEvents))
        .withHeaders("activity-more" -> more.toString)
      else Ok(p.activity.activity(displayEvents, listParams, more))
    }
  }

  def browseUsers = withUserAction.async { implicit user => implicit request =>
    // This is a bit gnarly because we want to get a searchable list
    // of users and combine it with a list of existing followers so
    // we can mark who's following and who isn't
    val filters = Map(SolrConstants.ACTIVE -> true.toString)
    val defaultParams = SearchParams(entities = List(EntityType.UserProfile), excludes = Some(List(user.id)),
          sort = Some(SearchOrder.Name), limit = Some(40))
    val searchParams = SearchParams.form.bindFromRequest.value
      .getOrElse(defaultParams).setDefault(Some(defaultParams))

    for {
      following <- backend.listFollowing(user.id, ListParams())
      blocked <- backend.listBlocked(user.id, ListParams())
      srch <- searchDispatcher.search(searchParams, Nil, Nil, filters)
      users <- searchResolver.resolve[UserProfile](srch.items)
    } yield Ok(p.social.browseUsers(user, srch.copy(items = users), searchParams, following))
  }

  def browseUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    val params = ListParams.fromRequest(request)
    val eventParams = SystemEventParams.fromRequest(request).copy(users = List(userId))
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)

    for {
      them <- backend.get[UserProfile](userId)
      theirActivity <- backend.listEvents(params, eventParams)
      followed <- backend.isFollowing(user.id, userId)
      canMessage <- canMessage(user.id, userId)
    } yield Ok(p.social.browseUser(them, theirActivity, followed, canMessage))
  }

  def followUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    ???
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

  def unfollowUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    ???
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

  def blockUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    ???
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

  def unblockUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    ???
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
      theirFollowers <- backend.pageFollowers(userId, params)
      whoImFollowing <- backend.listFollowing(user.id)
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
      theirFollowing <- backend.pageFollowing(userId, params)
      whoImFollowing <- backend.listFollowing(user.id)
    } yield {
      if (isAjax)
        Ok(p.social.followingList(them, theirFollowing, params, whoImFollowing))
      else
        Ok(p.social.listFollowing(them, theirFollowing, params, whoImFollowing))
    }
  }

  def watchItem(id: String) = withUserAction.async { implicit user => implicit request =>
    ???
  }

  def watchItemPost(id: String) = withUserAction.async { implicit user => implicit request =>
    backend.watch(user.id, id).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def unwatchItem(id: String) = withUserAction.async { implicit user => implicit request =>
    ???
  }

  def unwatchItemPost(id: String) = withUserAction.async { implicit user => implicit request =>
    backend.unwatch(user.id, id).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(socialRoutes.browseUsers())
      }
    }
  }

  def watching = withUserAction.async { implicit user => implicit request =>
    val watchParams = PageParams.fromRequest(request)
    backend.pageWatching(user.id, watchParams).map { watchList =>
      Ok(p.profile.watchedItems(watchList))
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
   *  - they've got an account
   *  - they have messaging enabled
   *  - they're not blocking the current user
   */
  private def canMessage(userId: String, otherId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    // First, find their account. If we don't have
    // an account we don't have an email, so we can't
    // message them...
    userDAO.findByProfileId(otherId).filter(_.allowMessaging).map { account =>
      // If they've got messaging disabled we can't mail them...
      if (!account.allowMessaging) Future.successful(false)
        // And nor can we if they're blocking us specifically ;(
      else backend.isBlocking(otherId, userId).map(blocking => !blocking)
      backend.isBlocking(otherId, userId).map(blocking => !blocking)
    }.getOrElse(Future.successful(false))
  }

  private def sendMessageEmail(from: UserProfile, to: UserProfile, subject: String, message: String)(implicit request: RequestHeader): Boolean = {
    (for {
      accFrom <- userDAO.findByProfileId(from.id)
      accTo <- userDAO.findByProfileId(to.id)
    } yield {
      import com.typesafe.plugin._
      use[MailerPlugin].email
        .setSubject(s"EHRI: Message from ${from.toStringLang}: $subject")
        .setRecipient(accTo.email)
        .setReplyTo(accFrom.email)
        .setFrom("EHRI Email Validation <noreply@ehri-project.eu>")
        .send(views.txt.p.social.mail.messageEmail(from, subject, message).body,
        views.html.p.social.mail.messageEmail(from, subject, message).body)
      true
    }).getOrElse(false)
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
        BadRequest(Messages("portal.social.sendMessage.userNotAcceptingMessages"))
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
          .withGlobalError("portal.social.sendMessage.userNotAcceptingMessages"))
      } else {
        boundForm.fold(
          errForm => onError(userTo, errForm),
          data => {
            val (subject, message) = data
            sendMessageEmail(user, userTo, subject, message)
            val msg = Messages("portal.social.messageSent")
            if (isAjax) Ok(Json.obj("ok" -> msg))
            else Redirect(socialRoutes.browseUser(userId))
              .flashing("success" -> msg)
          }
        )
      }
    }
  }
}
