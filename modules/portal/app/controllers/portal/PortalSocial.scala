package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import controllers.base.{AuthController, ControllerHelpers}
import models.UserProfile
import views.html.p
import utils.{PageParams, SystemEventParams, ListParams}
import backend.rest.{SearchDAO, PermissionDenied}
import utils.search.{SearchOrder, Dispatcher, SearchParams}
import defines.EntityType
import backend.ApiUser


/**
 * @author Mike Bryant (http://github.com/mikesname)
 *
 * NB: Things like watching and following items could
 * be greatly optimised by implementing caching for
 * just lists of IDs.
 */
trait PortalSocial {
  self: Controller with ControllerHelpers with AuthController =>

  val searchDispatcher: Dispatcher
  private val searchDao = new SearchDAO

  def browseUsers = withUserAction.async { implicit user => implicit request =>
    // This is a bit gnarly because we want to get a searchable list
    // of users and combine it with a list of existing followers so
    // we can mark who's following and who isn't
    val defaultParams = SearchParams(entities = List(EntityType.UserProfile), excludes = Some(List(user.id)),
          sort = Some(SearchOrder.Name), limit = Some(40))
    val searchParams = SearchParams.form.bindFromRequest.value
      .getOrElse(defaultParams).setDefault(Some(defaultParams))

    for {
      followers <- backend.listFollowing(user.id, ListParams())
      srch <- searchDispatcher.search(searchParams, Nil, Nil)
      users <- searchDao.list[UserProfile](srch.items.map(_.itemId))
    } yield Ok(p.social.browseUsers(user, srch.copy(items = users), searchParams, followers))
  }

  def browseUser(userId: String) = withUserAction.async { implicit user => implicit request =>
    val params = ListParams.fromRequest(request)
    val eventParams = SystemEventParams.fromRequest(request).copy(users = List(userId))
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
}
