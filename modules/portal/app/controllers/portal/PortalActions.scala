package controllers.portal

import play.api.libs.concurrent.Execution.Implicits._
import defines.EntityType
import utils.{ListParams, PageParams}
import models.{Link, Annotation, UserProfile}
import play.api.mvc.{Result, AnyContent, Request}
import models.json.{ClientConvertable, RestReadable}
import rest.EntityDAO
import controllers.base.{ControllerHelpers, AuthController}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalActions {

  self: AuthController with ControllerHelpers =>

  def pageAction[MT](entityType: EntityType.Value, paramsOpt: Option[PageParams] = None)(f: rest.Page[MT] => PageParams => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rd: RestReadable[MT]) = {
    userProfileAction { implicit userOpt => implicit request =>
      AsyncRest {
        val params = paramsOpt.getOrElse(PageParams.fromRequest(request))
        EntityDAO(entityType, userOpt).page(params).map { pageOrErr =>
          pageOrErr.right.map { list =>
            f(list)(params)(userOpt)(request)
          }
        }
      }
    }
  }

  def listAction[MT](entityType: EntityType.Value, paramsOpt: Option[ListParams] = None)(f: List[MT] => ListParams => Option[UserProfile] => Request[AnyContent] => Result)(
    implicit rd: RestReadable[MT]) = {
    userProfileAction { implicit userOpt => implicit request =>
      AsyncRest {
        val params = paramsOpt.getOrElse(ListParams.fromRequest(request))
        EntityDAO(entityType, userOpt).list(params).map { listOrErr =>
          listOrErr.right.map { list =>
            f(list)(params)(userOpt)(request)
          }
        }
      }
    }
  }

  /**
   * Fetch a given item, along with its links and annotations.
   */
  def getAction[MT](entityType: EntityType.Value, id: String)(
    f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
                     implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    itemAction[MT](entityType, id) { item => implicit userOpt => implicit request =>
      AsyncRest {
        val annsReq = rest.AnnotationDAO(userOpt).getFor(id)
        val linkReq = rest.LinkDAO(userOpt).getFor(id)
        for { annOrErr <- annsReq ; linkOrErr <- linkReq } yield {
          for { anns <- annOrErr.right ; links <- linkOrErr.right } yield {
            f(item)(anns)(links)(userOpt)(request)
          }
        }
      }
    }
  }

  /**
   * Fetch a given item with links, annotations, and a page of child items.
   */
  def getWithChildrenAction[CT, MT](entityType: EntityType.Value, id: String)(
    f: MT => rest.Page[CT] => PageParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
                                     implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
    getAction[MT](entityType, id) { item => anns => links => implicit userOpt => implicit request =>
      AsyncRest {
        val params = PageParams.fromRequest(request)
        val cReq = rest.EntityDAO[MT](entityType, userOpt).pageChildren[CT](id, params)
        for { cOrErr <- cReq  } yield {
          for { children <- cOrErr.right } yield {
            f(item)(children)(params)(anns)(links)(userOpt)(request)
          }
        }
      }
    }
  }
}
