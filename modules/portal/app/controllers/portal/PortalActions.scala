package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import defines.{ContentTypes, EntityType}
import utils.{FutureCache, PageParams}
import models.{Link, Annotation, UserProfile}
import play.api.mvc._
import models.json.{RestResource, ClientConvertable, RestReadable}
import controllers.base.{ControllerHelpers, AuthController}
import backend.Page
import models.base.AnyModel
import play.api.mvc.Result
import backend.ApiUser
import scala.concurrent.Future


case class ItemDetails(
  annotations: Page[Annotation],
  links: Page[Link],
  watched: Seq[AnyModel] = Nil
) {
  def isWatching(item: AnyModel): Boolean =
    watched.exists(_.id == item.id)
}

case class UserDetails(
  userOpt: Option[UserProfile] = None,
  watchedItems: Seq[AnyModel] = Nil
)

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalActions {

  self: AuthController with ControllerHelpers =>

  /**
   * Ensure that functions requiring an optional user in scope
   * can retrieve it automatically from a user details object.
   */
  implicit def userFromDetails(implicit details: UserDetails): Option[UserProfile] = details.userOpt

  /**
   * Fetched watched items for an optional user.
   */
  def watchedItems(implicit userOpt: Option[UserProfile]): Future[Page[AnyModel]] =
    watchedItems1(userOpt.map(_.id))

  def watchedItems1(userId: Option[String]): Future[Page[AnyModel]] = userId.map { id =>
    implicit val apiUser = ApiUser(Some(id))
    backend.watching(id, PageParams.empty.withoutLimit)
  }.getOrElse(Future.successful(Page.empty[AnyModel]))


  /**
   * Action which fetches a user's profile and list of watched items.
   */
  object userBrowseAction {
    def async(f: UserDetails => Request[AnyContent] => Future[Result]): Action[AnyContent] = {
      optionalUserAction.async { accountOpt => request =>
        accountOpt.map { account =>
          implicit val apiUser: ApiUser = ApiUser(Some(account.id))
          val userF: Future[UserProfile] = backend.get[UserProfile](account.id)
          val watchedF: Future[Seq[AnyModel]] = watchedItems1(userId = Some(account.id))
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

  def pageAction[MT](entityType: EntityType.Value, paramsOpt: Option[PageParams] = None)(
      f: Page[MT] => PageParams => Seq[AnyModel] => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rs: RestResource[MT], rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = paramsOpt.getOrElse(PageParams.fromRequest(request))
      val pageF: Future[Page[MT]] = backend.list(params)
      val watchedF: Future[Seq[AnyModel]] = watchedItems
      for {
        page <- pageF
        watched <- watchedF
      } yield f(page)(params)(watched)(userOpt)(request)
    }
  }

  def listAction[MT](entityType: EntityType.Value, paramsOpt: Option[PageParams] = None)(f: Page[MT] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rs: RestResource[MT], rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = paramsOpt.getOrElse(PageParams.fromRequest(request))
      backend.list(params).map { list =>
        f(list)(params)(userOpt)(request)
      }
    }
  }

  

  /**
   * Fetch a given item, along with its links and annotations.
   */
  object getAction {
    def async[MT](entityType: EntityType.Value, id: String)(
      f: MT => ItemDetails => Option[UserProfile] => Request[AnyContent] => Future[Result])(
                   implicit rs: RestResource[MT], rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      itemPermissionAction.async[MT](contentType = ContentTypes.withName(entityType.toString), id) {
          item => implicit userOpt => implicit request =>
        val watchedF: Future[Page[AnyModel]] = watchedItems
        val annotationF: Future[Page[Annotation]] = backend.getAnnotationsForItem(id)
        val linksF: Future[Page[Link]] = backend.getLinksForItem(id)
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
                   implicit rs: RestResource[MT], rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t))))))
    }
  }

  /**
   * Fetch a given item with links, annotations, and a page of child items.
   */
  object getWithChildrenAction {
    def async[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => Page[CT] => PageParams =>  ItemDetails => Option[UserProfile] => Request[AnyContent] => Future[Result])(
                       implicit rs: RestResource[MT], rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      getAction.async[MT](entityType, id) { item => details => implicit userOpt => implicit request =>
        val params = PageParams.fromRequest(request)
        backend.listChildren[MT,CT](id, params).flatMap { children =>
          f(item)(children)(params)(details)(userOpt)(request)
        }
      }
    }

    def apply[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => Page[CT] => PageParams =>  ItemDetails => Option[UserProfile] => Request[AnyContent] => Result)(
                       implicit rs: RestResource[MT], rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t))))))))
    }
  }
}
