package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.AccessibleEntity
import play.api.mvc._
import models.{Annotation,Entity,UserProfile}
import rest.Page

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam T
 */
trait EntityRead[T <: AccessibleEntity] extends EntityController[T] {
  val DEFAULT_LIMIT = 20

  def getEntity(id: String, user: Option[UserProfile])(f: Entity => Result)(implicit request: RequestHeader) = {
    implicit val maybeUser = user
    AsyncRest {
      rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
        itemOrErr.right.map(f)
      }
    }
  }

  def getGroups(user: Option[UserProfile])(f: Seq[(String,String)] => Seq[(String,String)] => Result)(implicit request: RequestHeader) = {
    implicit val maybeUser = user
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

  def getJson(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      import play.api.libs.json.Json
      AsyncRest {
        rest.EntityDAO(entityType, maybeUser).get(id).map { itemOrErr =>
          itemOrErr.right.map {
            item => Ok(Json.toJson(item.data))
          }
        }
      }
  }

  def getAction(id: String)(f: Entity => Map[String,List[Annotation]] => Option[UserProfile] => Request[AnyContent] => Result) = {
    itemPermissionAction(contentType, id) { item => implicit maybeUser =>
      implicit request =>
        AsyncRest {
          // NB: Effectively disable paging here by using a high limit
          val annsReq = rest.AnnotationDAO(maybeUser).getFor(id)
          for { annOrErr <- annsReq } yield {
            for { anns <- annOrErr.right } yield {
              f(item)(anns)(maybeUser)(request)
            }
          }
        }
    }
  }

  def listAction(page: Int = 1, limit: Int = DEFAULT_LIMIT)(f: rest.Page[Entity] => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction { implicit maybeUser =>
      implicit request =>
        AsyncRest {
          rest.EntityDAO(entityType, maybeUser)
            .page(math.max(page, 1), math.max(limit, 1)).map { itemOrErr =>
            itemOrErr.right.map {
              lst => f(lst)(maybeUser)(request)
            }
          }
        }
    }
  }
}
