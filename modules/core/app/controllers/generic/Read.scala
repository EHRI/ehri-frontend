package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models._
import utils.{RangePage, RangeParams, Page, PageParams}

import scala.concurrent.Future
import backend.{BackendReadable, BackendContentType, BackendResource}
import play.api.mvc.Result

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam MT Meta-model
 */
trait Read[MT] extends Generic[MT] {

  case class ItemPermissionRequest[A](
    item: MT,
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  case class ItemMetaRequest[A](
    item: MT,
    annotations: Page[Annotation],
    links: Page[Link],
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  /**
   * Fetch an item of type `MT` along with its item-level and scoped permissions.
   *
   * @param itemId The item's global ID
   * @return a request populated with the item and the user profile with permissions for that item
   */
  protected def ItemPermissionTransformer(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], rs: BackendResource[MT]) = new ActionTransformer[OptionalProfileRequest, ItemPermissionRequest] {
    def transform[A](input: OptionalProfileRequest[A]): Future[ItemPermissionRequest[A]] = {
      implicit val userOpt = input.profileOpt
      input.profileOpt.map { profile =>
        val itemF = backend.get[MT](itemId)
        val scopedPermsF = backend.getScopePermissions(profile.id, itemId)
        val permsF = backend.getItemPermissions(profile.id, ct.contentType, itemId)
        for {
          item <- itemF
          scopedPerms <- scopedPermsF
          perms <- permsF
          newProfile = profile.copy(itemPermissions = Some(perms), globalPermissions = Some(scopedPerms))
        } yield ItemPermissionRequest[A](item, Some(profile), input)
      }.getOrElse {
        for {
          item <- backend.get[MT](itemId)
        } yield ItemPermissionRequest[A](item, None, input)
      }
    }
  }

  /**
   * Fetch annotations and links for an item.
   *
   * @param itemId the item ID
   * @return a request populated with item data (links, annotations) and permission info
   */
  protected def ItemMetaTransformer(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], rs: BackendResource[MT]) = new ActionTransformer[ItemPermissionRequest, ItemMetaRequest] {
    def transform[A](input: ItemPermissionRequest[A]): Future[ItemMetaRequest[A]] = {
      implicit val userOpt = input.profileOpt
      val annotationsF = backend.getAnnotationsForItem[Annotation](itemId)
      val linksF = backend.getLinksForItem[Link](itemId)
      for {
        annotations <- annotationsF
        links <- linksF
      } yield ItemMetaRequest[A](input.item, annotations, links, input.profileOpt, input)
    }
  }

  def ItemPermissionAction(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], rs: BackendResource[MT]) =
    OptionalProfileAction andThen ItemPermissionTransformer(itemId)

  def ItemMetaAction(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], rs: BackendResource[MT]) =
    ItemPermissionAction(itemId) andThen ItemMetaTransformer(itemId)


  object getEntity {
    def async(id: String, user: Option[UserProfile])(f: MT => Future[Result])(
        implicit rd: BackendReadable[MT], rs: BackendResource[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      backend.get(id).flatMap { item =>
        f(item)
      }
    }

    def apply(id: String, user: Option[UserProfile])(f: MT => Result)(
        implicit rd: BackendReadable[MT], rs: BackendResource[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(id, user)(f.andThen(t => Future.successful(t)))
    }
  }

  object getEntityT {
    def async[T](resource: BackendResource[T], id: String)(f: T => Future[Result])(
        implicit userOpt: Option[UserProfile], request: RequestHeader, rd: BackendReadable[T], rs: BackendResource[MT]): Future[Result] = {
      backend.get[T](resource, id).flatMap { item =>
        f(item)
      }
    }
    def apply[T](resource: BackendResource[T], id: String)(f: T => Result)(
      implicit rd: BackendReadable[T], rs: BackendResource[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(resource, id)(f.andThen(t => Future.successful(t)))
    }
  }

  object getAction {
    def async(id: String)(f: MT => Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Future[Result])(
        implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      itemPermissionAction.async[MT](id) { item => implicit maybeUser => implicit request =>
          // NB: Effectively disable paging here by using a high limit
        val annsReq = backend.getAnnotationsForItem[Annotation](id)
        val linkReq = backend.getLinksForItem[Link](id)
        for {
          anns <- annsReq
          links <- linkReq
          r <- f(item)(anns)(links)(maybeUser)(request)
        } yield r
      }
    }

    def apply(id: String)(f: MT => Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))
    }
  }

  object getWithChildrenAction {
    def async[CT](id: String)(f: MT => Page[CT] => PageParams =>  Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Future[Result])(
          implicit rd: BackendReadable[MT], ct: BackendContentType[MT], crd: BackendReadable[CT]) = {
      ItemMetaAction(id).async { implicit request =>
        implicit val userOpt = request.profileOpt
        val params = PageParams.fromRequest(request)
        for {
          children <- backend.listChildren[MT,CT](id, params)
          r <- f(request.item)(children)(params)(request.annotations)(request.links)(request.profileOpt)(request)
        } yield r
      }
    }

    def apply[CT](id: String)(f: MT => Page[CT] => PageParams =>  Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], ct: BackendContentType[MT], crd: BackendReadable[CT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))))
    }
  }

  def pageAction(f: Page[MT] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: BackendReadable[MT], rs: BackendResource[MT]) = {
    OptionalProfileAction.async { implicit request =>
      val params = PageParams.fromRequest(request)
      backend.list(params).map { page =>
        f(page)(params)(request.profileOpt)(request)
      }
    }
  }

  def historyAction(id: String)(
      f: MT => RangePage[SystemEvent] => RangeParams => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], rs: BackendResource[MT]) = {
    OptionalProfileAction.async { implicit request =>
      val params = RangeParams.fromRequest(request)
      val getF: Future[MT] = backend.get(id)
      val historyF: Future[RangePage[SystemEvent]] = backend.history[SystemEvent](id, params)
      for {
        item <- getF
        events <- historyF
      } yield f(item)(events)(params)(request.profileOpt)(request)
    }
  }

  def versionsAction(id: String)(
    f: MT => Page[Version] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], rs: BackendResource[MT]) = {
    OptionalProfileAction.async { implicit request =>
      val params = PageParams.fromRequest(request)
      val getF: Future[MT] = backend.get(id)
      val versionsF: Future[Page[Version]] = backend.versions[Version](id, params)
      for {
        item <- getF
        events <- versionsF
      } yield f(item)(events)(params)(request.profileOpt)(request)
    }
  }
}
