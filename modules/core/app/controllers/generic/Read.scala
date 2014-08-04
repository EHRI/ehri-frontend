package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models._
import models.json.{RestResource, RestReadable, ClientConvertable}
import utils.PageParams
import backend.Page

import scala.concurrent.Future

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam MT Meta-model
 */
trait Read[MT] extends Generic[MT] {
  val DEFAULT_LIMIT = 20

  val defaultPage: PageParams = new PageParams()
  val defaultChildPage: PageParams = new PageParams()

  object getEntity {
    def async(id: String, user: Option[UserProfile])(f: MT => Future[Result])(
        implicit rd: RestReadable[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      backend.get(id).flatMap { item =>
        f(item)
      }
    }

    def apply(id: String, user: Option[UserProfile])(f: MT => Result)(
        implicit rd: RestReadable[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(id, user)(f.andThen(t => Future.successful(t)))
    }
  }

  object getEntityT {
    def async[T](resource: RestResource[T], id: String)(f: T => Future[Result])(
        implicit userOpt: Option[UserProfile], request: RequestHeader, rd: RestReadable[T]): Future[Result] = {
      backend.get[T](resource, id).flatMap { item =>
        f(item)
      }
    }
    def apply[T](resource: RestResource[T], id: String)(f: T => Result)(
      implicit rd: RestReadable[T], userOpt: Option[UserProfile], request: RequestHeader): Future[Result] = {
      async(resource, id)(f.andThen(t => Future.successful(t)))
    }
  }

  object getAction {
    def async(id: String)(f: MT => Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Future[Result])(
        implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
      itemPermissionAction.async[MT](contentType, id) { item => implicit maybeUser => implicit request =>
          // NB: Effectively disable paging here by using a high limit
        val annsReq = backend.getAnnotationsForItem(id)
        val linkReq = backend.getLinksForItem(id)
        for {
          anns <- annsReq
          links <- linkReq
          r <- f(item)(anns)(links)(maybeUser)(request)
        } yield r
      }
    }

    def apply(id: String)(f: MT => Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))
    }
  }

  object getWithChildrenAction {
    def async[CT](id: String)(f: MT => Page[CT] => PageParams =>  Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Future[Result])(
          implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
      itemPermissionAction.async[MT](contentType, id) { item => implicit userOpt => implicit request =>
        val params = PageParams.fromRequest(request)
        for {
          anns <- backend.getAnnotationsForItem(id)
          children <- backend.listChildren[MT,CT](id, params)
          links <- backend.getLinksForItem(id)
          r <- f(item)(children)(params)(anns)(links)(userOpt)(request)
        } yield r
      }
    }

    def apply[CT](id: String)(f: MT => Page[CT] => PageParams =>  Page[Annotation] => Page[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))))
    }
  }

  def pageAction(f: Page[MT] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      backend.list(params).map { page =>
        f(page)(params)(userOpt)(request)
      }
    }
  }

  def historyAction(id: String)(
      f: MT => Page[SystemEvent] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      val getF: Future[MT] = backend.get(id)
      val historyF: Future[Page[SystemEvent]] = backend.history(id, params)
      for {
        item <- getF
        events <- historyF
      } yield f(item)(events)(params)(userOpt)(request)
    }
  }

  def versionsAction(id: String)(
    f: MT => Page[Version] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      val getF: Future[MT] = backend.get(id)
      val versionsF: Future[Page[Version]] = backend.versions(id, params)
      for {
        item <- getF
        events <- versionsF
      } yield f(item)(events)(params)(userOpt)(request)
    }
  }
}
