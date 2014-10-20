package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import utils.{FutureCache, Page, PageParams}
import models.{Link, Annotation, UserProfile}
import play.api.mvc._
import controllers.base.{ControllerHelpers, AuthController}
import play.api.mvc.Result
import backend.{BackendReadable, BackendContentType, ApiUser}
import scala.concurrent.Future
import models.view.{UserDetails, ItemDetails}
import play.api.Play.current
import play.api.cache.Cache
import models.base.AnyModel


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalBase {

  self: AuthController with ControllerHelpers =>

  /**
   * Ensure that functions requiring an optional user in scope
   * can retrieve it automatically from a user details object.
   */
  implicit def userFromDetails(implicit details: UserDetails): Option[UserProfile] = details.userOpt

  private def userWatchCacheKey(userId: String) = s"$userId-watchlist"

  protected def clearWatchedItemsCache(userId: String) = Cache.remove(userWatchCacheKey(userId))

  /**
   * Fetched watched items for an optional user.
   */
  private def watchedItemIds(implicit userIdOpt: Option[String]): Future[Seq[String]] = userIdOpt.map { userId =>
    FutureCache.getOrElse(userWatchCacheKey(userId), 20 * 60) {
      implicit val apiUser: ApiUser = ApiUser(Some(userId))
      backend.watching[AnyModel](userId, PageParams.empty.withoutLimit).map { page =>
        page.items.map(_.id)
      }
    }
  }.getOrElse(Future.successful(Seq.empty))

  /**
   * Action which fetches a user's profile and list of watched items.
   */
  object userBrowseAction {
    def async(f: UserDetails => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      optionalUserAction.async { accountOpt => request =>
        accountOpt.map { account =>
          implicit val apiUser: ApiUser = ApiUser(Some(account.id))
          val userF: Future[UserProfile] = backend.get[UserProfile](account.id)
          val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(account.id))
          for {
            user <- userF
            userWithAccount = user.copy(account=Some(account))
            watched <- watchedF
            r <- f(UserDetails(Some(userWithAccount), watched))(request)
          } yield r
        } getOrElse {
          f(new UserDetails)(request)
        }
      }
    }

    def apply(f: UserDetails => Request[AnyContent] => Result): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => Future.successful(t))))
    }
  }

  /**
   * Fetch a given item, along with its links and annotations.
   */
  object getAction {
    def async[MT](entityType: EntityType.Value, id: String)(
      f: MT => ItemDetails => Option[UserProfile] => Request[AnyContent] => Future[Result])(
                   implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] = {
      itemPermissionAction.async[MT](id) {
          item => implicit userOpt => implicit request =>
        val watchedF: Future[Seq[String]] = watchedItemIds(userOpt.map(_.id))
        val annotationF: Future[Page[Annotation]] = backend.getAnnotationsForItem[Annotation](id)
        val linksF: Future[Page[Link]] = backend.getLinksForItem[Link](id)
        for {
          watched <- watchedF
          anns <- annotationF
          links <- linksF
          r <- f(item)(ItemDetails(anns, links, watched))(userOpt)(request)
        } yield r
      }
    }

    def apply[MT](entityType: EntityType.Value, id: String)(
      f: MT => ItemDetails => Option[UserProfile] => Request[AnyContent] => Result)(
                   implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t))))))
    }
  }
}
