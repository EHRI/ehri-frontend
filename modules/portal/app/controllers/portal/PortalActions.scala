package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import defines.{PermissionType, EntityType}
import utils.{ListParams, PageParams}
import models.{AnnotationF, Link, Annotation, UserProfile}
import play.api.mvc._
import models.json.{RestResource, ClientConvertable, RestReadable}
import controllers.base.{ControllerHelpers, AuthController}
import scala.concurrent.Future
import backend.Page
import models.base.AnyModel
import play.api.data.Form
import models.forms.AnnotationForm
import scala.Some
import play.api.mvc.SimpleResult
import backend.ApiUser
import defines.ContentTypes
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


case class ItemDetails(
  annotations: Map[String,List[Annotation]],
  links: List[Link],
  watched: Boolean
)

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
  def watchedItems(implicit userOpt: Option[UserProfile]): Future[List[AnyModel]] =
    userOpt.map(user => backend.listWatching(user.id, ListParams()))
      .getOrElse(Future.successful(List.empty[AnyModel]))


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
            watched <- backend.listWatching(account.id)
            r <- f(UserDetails(Some(user), watched))(request)
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
      itemAction.async[MT](entityType, id) { item => implicit userOpt => implicit request =>

        def isWatching =
          if (userOpt.isDefined)backend.isWatching(userOpt.get.id, id)
          else Future.successful(false)

        for {
          watching <- isWatching
          anns <- backend.getAnnotationsForItem(id)
          links <- backend.getLinksForItem(id)
          r <- f(item)(ItemDetails(anns, links, watching))(userOpt)(request)
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

  def annotationAction[MT](id: String, contentType: ContentTypes.Value)(f: MT => Form[AnnotationF] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]): Action[AnyContent] = {
    withItemPermission[MT](id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      f(item)(AnnotationForm.form.bindFromRequest)(userOpt)(request)
    }
  }

  def annotationPostAction[MT](id: String, contentType: ContentTypes.Value)(f: Either[Form[AnnotationF],Annotation] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>

      // TODO: Define visibility rules here and bind them
      // from a custom form declaring something like: private (me only)
      // my groups, or global visibility.

      AnnotationForm.form.bindFromRequest.fold(
        errorForm => immediate(f(Left(errorForm))(userOpt)(request)),
        ann => backend.createAnnotation(id, ann).map { ann =>
          f(Right(ann))(userOpt)(request)
        }
      )
    }
  }
}
