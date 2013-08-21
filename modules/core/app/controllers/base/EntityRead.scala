package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.{Model, MetaModel}
import play.api.mvc._
import models._
import rest.{Page, RestPageParams}
import controllers.ListParams
import models.json.{RestReadable, ClientConvertable}
import play.api.libs.json.{Format, Json}
import play.api.Logger
import play.api.http.MimeTypes


/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam MT Meta-model
 */
trait EntityRead[MT] extends EntityController {
  val DEFAULT_LIMIT = 20

  val defaultPage: RestPageParams = new RestPageParams()
  val defaultChildPage: RestPageParams = new RestPageParams()


  def getEntity(id: String, user: Option[UserProfile])(f: MT => Result)(
      implicit rd: RestReadable[MT], userOpt: Option[UserProfile], request: RequestHeader) = {
    AsyncRest {
      rest.EntityDAO(entityType, userOpt).get(id).map { itemOrErr =>
        itemOrErr.right.map(f)
      }
    }
  }

  def getEntity[T](otherType: defines.EntityType.Type, id: String)(f: T => Result)(
      implicit userOpt: Option[UserProfile], request: RequestHeader, rd: RestReadable[T]) = {
    AsyncRest {
      rest.EntityDAO[T](otherType, userOpt).get(id).map { itemOrErr =>
        itemOrErr.right.map(f)
      }
    }
  }

  def getUsersAndGroups(f: Seq[(String,String)] => Seq[(String,String)] => Result)(implicit userOpt: Option[UserProfile], request: RequestHeader) = {
    // TODO: Handle REST errors
    Async {
      for {
        users <- rest.RestHelpers.getUserList
        groups <- rest.RestHelpers.getGroupList
      } yield {
        f(users)(groups)
      }
    }
  }

  def getAction(id: String)(f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
    itemPermissionAction[MT](contentType, id) { item => implicit maybeUser => implicit request =>
      Secured {
        render {
          case Accepts.Json() => Ok(Json.toJson(item)(crd.clientFormat))
          case _ => {
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
      }
    }
  }

  def getWithChildrenAction[CT](id: String)(
      f: MT => rest.Page[CT] => RestPageParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
          implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
    itemPermissionAction[MT](contentType, id) { item => implicit userOpt => implicit request =>
      Secured {
        render {
          case Accepts.Json() => Ok(Json.toJson(item)(cfmt.clientFormat))
          case _ => {
            AsyncRest {
              // NB: Effectively disable paging here by using a high limit
              val params = RestPageParams.fromRequest(request)
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
      }
    }
  }

  def listAction(f: rest.Page[MT] => RestPageParams => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    userProfileAction { implicit userOpt => implicit request =>
      Secured {
        AsyncRest {
          val params = RestPageParams.fromRequest(request)
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
  }


  def historyAction(id: String)(
      f: MT => rest.Page[SystemEvent] => RestPageParams => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    userProfileAction { implicit userOpt => implicit request =>
      Secured {
        AsyncRest {
          val params = RestPageParams.fromRequest(request)
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
}
