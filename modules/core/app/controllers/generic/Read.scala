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

  case class ItemRequest[A](
    item: MT,
    annotations: Page[Annotation],
    links: Page[Link],
    profileOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  def ItemAction(itemId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT], rs: BackendResource[MT]) = new ActionTransformer[OptionalProfileRequest, ItemRequest] {
    def transform[A](input: OptionalProfileRequest[A]): Future[ItemRequest[A]] = {
      implicit val opr = input
      input.profileOpt.map { profile =>
        val itemF = backend.get[MT](itemId)
        val scopedPermsF = backend.getScopePermissions(profile.id, itemId)
        val permsF = backend.getItemPermissions(profile.id, ct.contentType, itemId)
        val annotationsF = backend.getAnnotationsForItem[Annotation](itemId)
        val linksF = backend.getLinksForItem[Link](itemId)
        for {
          item <- itemF
          scope <- scopedPermsF
          perms <- permsF
          anns <- annotationsF
          links <- linksF
          newProfile = profile.copy(itemPermissions = Some(perms), globalPermissions = Some(scope))
        } yield ItemRequest[A](item, anns, links, Some(profile), input)

      }.getOrElse {
        val itemF = backend.get[MT](itemId)
        val annotationsF = backend.getAnnotationsForItem[Annotation](itemId)
        val linksF = backend.getLinksForItem[Link](itemId)
        for {
          item <- itemF
          anns <- annotationsF
          links <- linksF
        } yield ItemRequest[A](item, anns, links, None, input)
      }
    }
  }


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
      itemPermissionAction.async[MT](id) { item => implicit userOpt => implicit request =>
        val params = PageParams.fromRequest(request)
        for {
          anns <- backend.getAnnotationsForItem[Annotation](id)
          children <- backend.listChildren[MT,CT](id, params)
          links <- backend.getLinksForItem[Link](id)
          r <- f(item)(children)(params)(anns)(links)(userOpt)(request)
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
