package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import defines.{ContentTypes, EntityType}
import utils.{FutureCache, ListParams, PageParams}
import models.{Link, Annotation, UserProfile}
import play.api.mvc._
import models.json.{RestResource, ClientConvertable, RestReadable}
import controllers.base.{ControllerHelpers, AuthController}
import backend.Page
import models.base.AnyModel
import play.api.mvc.SimpleResult
import backend.ApiUser
import scala.concurrent.Future


case class ItemDetails(
  annotations: Seq[Annotation],
  links: List[Link],
  watched: List[AnyModel] = Nil
) {
  def isWatching(item: AnyModel): Boolean =
    watched.exists(_.id == item.id)
}

case class UserDetails(
  userOpt: Option[UserProfile] = None,
  watchedItems: List[AnyModel] = Nil
)

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalActions {

  self: AuthController with ControllerHelpers =>

  implicit def userFromDetails(implicit details: UserDetails): Option[UserProfile] = details.userOpt

  /**
   * Fetched watched items for an optional user.
   */
  def watchedItems(implicit userOpt: Option[UserProfile]): Future[List[AnyModel]] = {
    userOpt.map(user => {
      // TODO: Figure out a caching strategy that isn't too fragile
      backend.listWatching(user.id, ListParams.empty.withoutLimit)
    }).getOrElse(Future.successful(List.empty[AnyModel]))
  }


  /**
   * Action which fetches a user's profile and list of watched items.
   */
  object userBrowseAction {
    def async(f: UserDetails => Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = {
      optionalUserAction.async { accountOpt => request =>
        accountOpt.map { account =>
          implicit val apiUser: ApiUser = ApiUser(Some(account.id))
          for {
            user <- backend.get[UserProfile](account.id)
            userWithAccount = user.copy(account=Some(account))
            watched <- watchedItems(Some(user))
            r <- f(UserDetails(Some(userWithAccount), watched))(request)
          } yield r
        } getOrElse {
          f(new UserDetails)(request)
        }
      }
    }

    def apply(f: UserDetails => Request[AnyContent] => SimpleResult): Action[AnyContent] = {
      async(f.andThen(_.andThen(t => Future.successful(t))))
    }
  }

  def pageAction[MT](entityType: EntityType.Value, paramsOpt: Option[PageParams] = None)(
      f: Page[MT] => PageParams => List[AnyModel] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
    implicit rs: RestResource[MT], rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = paramsOpt.getOrElse(PageParams.fromRequest(request))
      for {
        page <- backend.page(params)
        watched <- watchedItems
      } yield f(page)(params)(watched)(userOpt)(request)
    }
  }

  def listAction[MT](entityType: EntityType.Value, paramsOpt: Option[ListParams] = None)(f: List[MT] => ListParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
    implicit rs: RestResource[MT], rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = paramsOpt.getOrElse(ListParams.fromRequest(request))
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
      f: MT => ItemDetails => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
                   implicit rs: RestResource[MT], rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      itemPermissionAction.async[MT](contentType = ContentTypes.withName(entityType.toString), id) { item => implicit userOpt => implicit request =>
        def isWatching =
          if (userOpt.isDefined)backend.isWatching(userOpt.get.id, id)
          else Future.successful(false)

        for {
          watched <- watchedItems
          anns <- backend.getAnnotationsForItem(id)
          links <- backend.getLinksForItem(id)
          r <- f(item)(ItemDetails(anns, links, watched))(userOpt)(request)
        } yield r
      }
    }

    def apply[MT](entityType: EntityType.Value, id: String)(
      f: MT => ItemDetails => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
                   implicit rs: RestResource[MT], rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t))))))
    }
  }

  /**
   * Fetch a given item with links, annotations, and a page of child items.
   */
  object getWithChildrenAction {
    def async[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => Page[CT] => PageParams =>  ItemDetails => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
                       implicit rs: RestResource[MT], rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      getAction.async[MT](entityType, id) { item => details => implicit userOpt => implicit request =>
        val params = PageParams.fromRequest(request)
        backend.pageChildren[MT,CT](id, params).flatMap { children =>
          f(item)(children)(params)(details)(userOpt)(request)
        }
      }
    }

    def apply[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => Page[CT] => PageParams =>  ItemDetails => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
                       implicit rs: RestResource[MT], rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t))))))))
    }
  }
}
