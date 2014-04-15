package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import controllers.base.{SessionPreferences, AuthController, ControllerHelpers}
import models.UserProfile
import views.html.p
import utils.{SessionPrefs, PageParams, SystemEventParams, ListParams}
import utils.search.{Resolver, SearchOrder, Dispatcher, SearchParams}
import defines.{EventType, EntityType}
import play.api.Play._
import solr.SolrConstants


/**
 * @author Mike Bryant (http://github.com/mikesname)
 *
 * NB: Things like watching and following items could
 * be greatly optimised by implementing caching for
 * just lists of IDs.
 */
trait PortalSocial {
  self: Controller with ControllerHelpers with AuthController with SessionPreferences[SessionPrefs] =>

  val searchDispatcher: Dispatcher
  val searchResolver: Resolver

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

  def personalisedActivity = withUserAction.async { implicit user => implicit request =>
    val listParams = ListParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    backend.listEventsForUser(user.id, listParams, eventFilter).map { events =>
      Ok(p.activity.activity(events, listParams))
    }
  }

  def personalisedActivityMore(offset: Int) = withUserAction.async { implicit user => implicit request =>
    val listParams = ListParams.fromRequest(request).copy(offset = offset)
    val eventFilter = SystemEventParams.fromRequest(request)
      .copy(eventTypes = activityEventTypes)
      .copy(itemTypes = activityItemTypes)
    backend.listEventsForUser(user.id, listParams, eventFilter).map { events =>
      Ok(p.social.eventItems(events))
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
      followers <- backend.listFollowing(user.id, ListParams())
      srch <- searchDispatcher.search(searchParams, Nil, Nil, filters)
      users <- searchResolver.resolve[UserProfile](srch.items)
    } yield Ok(p.social.browseUsers(user, srch.copy(items = users), searchParams, followers))
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
    } yield Ok(p.social.browseUser(them, theirActivity, followed))
  }

  def followUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    ???
  }

  def followUserPost(userId: String) = withUserAction.async { implicit user => implicit request =>
    backend.follow(user.id, userId).map { _ =>
      if (isAjax) {
        Ok("ok")
      } else {
        Redirect(controllers.portal.routes.Portal.browseUsers())
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
        Redirect(controllers.portal.routes.Portal.browseUsers())
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
        Redirect(controllers.portal.routes.Portal.browseUsers())
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
        Redirect(controllers.portal.routes.Portal.browseUsers())
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

  def sendMessage(userId: String) = withUserAction { implicit user => implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    ???
  }

  def sendMessagePost(userId: String) = withUserAction.async { implicit user => implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    checkRecapture.map { ok =>
      if (!ok) {
        val form = messageForm.withGlobalError("error.badRecaptcha")
        ???
      } else {
        ???
      }
    }
  }
}
