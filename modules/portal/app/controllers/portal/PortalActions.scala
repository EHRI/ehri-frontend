package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import utils.{ListParams, PageParams}
import models.{Link, Annotation, UserProfile}
import play.api.mvc._
import models.json.{ClientConvertable, RestReadable}
import rest.EntityDAO
import controllers.base.{ControllerHelpers, AuthController}
import scala.concurrent.Future
import rest.EntityDAO
import play.api.mvc.SimpleResult

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalActions {

  self: AuthController with ControllerHelpers =>

  def pageAction[MT](entityType: EntityType.Value, paramsOpt: Option[PageParams] = None)(f: rest.Page[MT] => PageParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
    implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = paramsOpt.getOrElse(PageParams.fromRequest(request))
      EntityDAO(entityType, userOpt).page(params).map { page =>
        f(page)(params)(userOpt)(request)
      }
    }
  }

  def listAction[MT](entityType: EntityType.Value, paramsOpt: Option[ListParams] = None)(f: List[MT] => ListParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
    implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = paramsOpt.getOrElse(ListParams.fromRequest(request))
      EntityDAO(entityType, userOpt).list(params).map { list =>
        f(list)(params)(userOpt)(request)
      }
    }
  }

  /**
   * Fetch a given item, along with its links and annotations.
   */
  object getAction {
    def async[MT](entityType: EntityType.Value, id: String)(
      f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
                   implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      itemAction.async[MT](entityType, id) { item => implicit userOpt => implicit request =>
        val annsReq = rest.AnnotationDAO(userOpt).getFor(id)
        val linkReq = rest.LinkDAO(userOpt).getFor(id)
        for { anns <- annsReq ; links <- linkReq ; r <- f(item)(anns)(links)(userOpt)(request) } yield r
      }
    }

    def apply[MT](entityType: EntityType.Value, id: String)(
      f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
                   implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))
    }
  }

  /**
   * Fetch a given item with links, annotations, and a page of child items.
   */
  object getWithChildrenAction {
    def async[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => rest.Page[CT] => PageParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
                       implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      getAction.async[MT](entityType, id) { item => anns => links => implicit userOpt => implicit request =>
        val params = PageParams.fromRequest(request)
        rest.EntityDAO[MT](entityType, userOpt).pageChildren[CT](id, params).flatMap { children =>
          f(item)(children)(params)(anns)(links)(userOpt)(request)
        }
      }
    }

    def apply[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => rest.Page[CT] => PageParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
                       implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))))
    }
  }
}
