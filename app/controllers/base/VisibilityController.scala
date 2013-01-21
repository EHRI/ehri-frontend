package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models.{Entity,UserProfile}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam F the entity's formable class
 * @tparam T the entity's build class
 */
trait VisibilityController[T <: AccessibleEntity] extends EntityRead[T] {

  def visibilityAction(id: String)(f: Entity => Seq[(String,String)] => Seq[(String,String)] => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
            users <- rest.RestHelpers.getUserList
            groups <- rest.RestHelpers.getGroupList
          } yield {
            itemOrErr.right.map { item =>
              f(item)(users)(groups)(user)(request)
            }
          }
        }
    }
  }

  def visibilityPostAction(id: String)(f: Boolean => UserProfile => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { implicit user =>
      implicit request =>
        implicit val maybeUser = Some(user)
        val data = request.body.asFormUrlEncoded.getOrElse(List()).flatMap { case (_, s) => s.toList }
        AsyncRest {
          rest.VisibilityDAO(user).set(id, data.toList).map { boolOrErr =>
            boolOrErr.right.map { bool =>
              f(bool)(user)(request)
            }
          }
        }
    }
  }
}

