package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models._
import rest.Page
import models.json.{RestReadable, ClientConvertable}
import play.api.libs.json.Json
import utils.ListParams

import scala.concurrent.Future

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam MT Meta-model
 */
trait EntityRead[MT] extends EntityController {
  val DEFAULT_LIMIT = 20

  val defaultPage: ListParams = new ListParams()
  val defaultChildPage: ListParams = new ListParams()


  def getEntity(id: String, user: Option[UserProfile])(f: MT => SimpleResult)(
      implicit rd: RestReadable[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
    AsyncRest {
      rest.EntityDAO(entityType, userOpt).get(id).map { itemOrErr =>
        itemOrErr.right.map(f)
      }
    }
  }

  def getEntity[T](otherType: defines.EntityType.Type, id: String)(f: T => SimpleResult)(
      implicit userOpt: Option[UserProfile], request: RequestHeader, rd: RestReadable[T]): Future[SimpleResult] = {
    AsyncRest {
      rest.EntityDAO[T](otherType, userOpt).get(id).map { itemOrErr =>
        itemOrErr.right.map(f)
      }
    }
  }

  def getUsersAndGroups(f: Seq[(String,String)] => Seq[(String,String)] => SimpleResult)(
        implicit userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
    // TODO: Handle REST errors
      for {
        users <- rest.RestHelpers.getUserList
        groups <- rest.RestHelpers.getGroupList
      } yield {
        f(users)(groups)
      }
  }

  object getAction {
    def async[MT](id: String)(f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
        implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
      itemPermissionAction.async[MT](contentType, id) { item => implicit maybeUser => implicit request =>
        AsyncRest {
          // NB: Effectively disable paging here by using a high limit
          val annsReq = rest.AnnotationDAO(maybeUser).getFor(id)
          val linkReq = rest.LinkDAO(maybeUser).getFor(id)
          for { annOrErr <- annsReq ; linkOrErr <- linkReq } yield {
            for { anns <- annOrErr.right ; links <- linkOrErr.right } yield {
              f(item)(anns)(links)(maybeUser)(request)
            }
          }
        }
      }
    }

    def apply[MT](id: String)(f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
      async(id)(f.andThen(_.andThen(t => Future.successful(t))))
    }
  }

  def getWithChildrenAction[CT](id: String)(
      f: MT => rest.Page[CT] => ListParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
          implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
    itemPermissionAction.async[MT](contentType, id) { item => implicit userOpt => implicit request =>
      AsyncRest {
        // NB: Effectively disable paging here by using a high limit
        val params = ListParams.fromRequest(request)
        val annsReq = rest.AnnotationDAO(userOpt).getFor(id)
        val linkReq = rest.LinkDAO(userOpt).getFor(id)
        val cReq = rest.EntityDAO[MT](entityType, userOpt).pageChildren[CT](id, params)
        for { annOrErr <- annsReq ; cOrErr <- cReq ; linkOrErr <- linkReq } yield {
          for { anns <- annOrErr.right ; children <- cOrErr.right ; links <- linkOrErr.right } yield {
            f(item)(children)(params)(anns)(links)(userOpt)(request)
          }
        }
      }
    }
  }

  def listAction(f: rest.Page[MT] => ListParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      AsyncRest {
        val params = ListParams.fromRequest(request)
        rest.EntityDAO[MT](entityType, userOpt).page(params).map { itemOrErr =>
          itemOrErr.right.map {
            page => render {
              case Accepts.Json() => Ok(Json.toJson(page)(Page.pageWrites(cfmt.clientFormat)))
              case _ => f(page)(params)(userOpt)(request)
            }
          }
        }
      }
    }
  }


  def historyAction(id: String)(
      f: MT => rest.Page[SystemEvent] => ListParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      AsyncRest {
        val params = ListParams.fromRequest(request)
        val itemReq = rest.EntityDAO[MT](entityType, userOpt).get(id)(rd)
        val alReq = rest.SystemEventDAO(userOpt).history(id, params)
        for { itemOrErr <- itemReq ; alOrErr <- alReq  } yield {
          for { item <- itemOrErr.right ; al <- alOrErr.right  } yield {
            f(item)(al)(params)(userOpt)(request)
          }
        }
      }
    }
  }
}
