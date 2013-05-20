package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import play.api.mvc._
import models._
import rest.RestPageParams
import controllers.ListParams
import models.SystemEvent


/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam T
 */
trait EntityRead[T <: AccessibleEntity] extends EntityController[T] {
  val DEFAULT_LIMIT = 20

  val defaultPage: RestPageParams = new RestPageParams()
  val defaultChildPage: RestPageParams = new RestPageParams()

  def getEntity(id: String, user: Option[UserProfile])(f: Entity => Result)(implicit userOpt: Option[UserProfile], request: RequestHeader) = {
    AsyncRest {
      rest.EntityDAO(entityType, userOpt).get(id).map { itemOrErr =>
        itemOrErr.right.map(f)
      }
    }
  }

  def getEntity(otherType: defines.EntityType.Type, id: String)(f: Entity => Result)(implicit userOpt: Option[UserProfile], request: RequestHeader) = {
    AsyncRest {
      rest.EntityDAO(otherType, userOpt).get(id).map { itemOrErr =>
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

  def getJson(id: String) = userProfileAction { implicit maybeUser => implicit request =>
    import play.api.libs.json.Json
    AsyncRest {
      rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
        itemOrErr.right.map {
          item => Ok(Json.toJson(item.data))
        }
      }
    }
  }

  def getAction(id: String)(f: Entity => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result) = {
    itemPermissionAction(contentType, id) { item => implicit maybeUser => implicit request =>
      Secured {
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

  def getWithChildrenAction(id: String)(
      f: Entity => rest.Page[Entity] => ListParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result) = {
    itemPermissionAction(contentType, id) { item => implicit userOpt => implicit request =>
      Secured {
        AsyncRest {
          // NB: Effectively disable paging here by using a high limit
          val params = ListParams.bind(request)
          val annsReq = rest.AnnotationDAO(userOpt).getFor(id)
          val linkReq = rest.LinkDAO(userOpt).getFor(id)
          val cReq = rest.EntityDAO(entityType, userOpt).pageChildren(id, processChildParams(params))
          for { annOrErr <- annsReq ; cOrErr <- cReq ; linkOrErr <- linkReq } yield {
            for { anns <- annOrErr.right ; children <- cOrErr.right ; links <- linkOrErr.right } yield {
              f(item)(children)(params)(anns)(links)(userOpt)(request)
            }
          }
        }
      }
    }
  }

  def listAction(f: rest.Page[Entity] => ListParams => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction { implicit userOpt => implicit request =>
      Secured {
        AsyncRest {
          val params = ListParams.bind(request)
          rest.EntityDAO(entityType, userOpt).page(processParams(params)).map { itemOrErr =>
            itemOrErr.right.map {
              page => f(page)(params)(userOpt)(request)
            }
          }
        }
      }
    }
  }


  def historyAction[C <: AccessibleEntity](id: String)(
      f: Entity => rest.Page[SystemEvent] => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction { implicit userOpt => implicit request =>
      Secured {
        AsyncRest {
          val itemReq = rest.EntityDAO(entityType, userOpt).get(id)
          // NOTE: we do not use the controller's RestPageParams here because
          // they won't apply to Event items...???
          val alReq = rest.SystemEventDAO(userOpt).history(id, RestPageParams())
          for { itemOrErr <- itemReq ; alOrErr <- alReq  } yield {
            for { item <- itemOrErr.right ; al <- alOrErr.right  } yield {
              f(item)(al.copy(items = al.items.map(SystemEvent(_))))(userOpt)(request)
            }
          }
        }
      }
    }
  }

  /**
   * Process parameters for the main list form.
   * @param listParams
   * @return
   */
  def processParams(listParams: ListParams): RestPageParams = {
    // don't do anything by default except for copy the page and limit
    // Inheriting controllers can override this to handle translating
    // filter values to the internal REST format.
    RestPageParams(listParams.page, listParams.limit)
  }

  /**
   * Process parameters for the child form.
   * @param listParams
   * @return
   */
  def processChildParams(listParams: ListParams): RestPageParams = processParams(listParams)
}
