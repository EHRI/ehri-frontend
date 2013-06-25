package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models.{Entity,UserProfile}
import rest.EntityDAO

object VisibilityController {
  /**
   * Extract a list of accessors from the request params.
   * @param d
   * @return
   */
  def extractAccessors(d: Option[Map[String,Seq[String]]]): List[String] = {
    d.getOrElse(Map()).flatMap {
      case (k, s) if k == s"accessor_${EntityType.Group}" || k == s"accessor_${EntityType.UserProfile}" => s.toList
      case (_, s) => Nil
    }.toList
  }
}

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait VisibilityController[T <: AccessibleEntity] extends EntityRead[T] {

  def visibilityAction(id: String)(f: Entity => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfileMeta] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(item)(users)(groups)(userOpt)(request)
      }
    }
  }

  def visibilityPostAction(id: String)(f: Entity => Option[UserProfileMeta] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      val data = models.forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
      AsyncRest {
        rest.VisibilityDAO(userOpt).set(id, data).map { itemOrErr =>
          itemOrErr.right.map { item =>
            f(item)(userOpt)(request)
          }
        }
      }
    }
  }
}

