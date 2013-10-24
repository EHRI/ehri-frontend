package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import models.base.Persistable
import defines._
import models.{UserProfile, Entity}
import rest.EntityDAO
import models.json.RestReadable

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
 * @tparam MT the entity's meta class
 */
trait VisibilityController[MT] extends EntityRead[MT] {

  def visibilityAction(id: String)(f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      getUsersAndGroups { users => groups =>
        f(item)(users)(groups)(userOpt)(request)
      }
    }
  }

  def visibilityPostAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      val data = forms.VisibilityForm.form.bindFromRequest.value.getOrElse(Nil)
      rest.VisibilityDAO(userOpt).set[MT](id, data).map { item =>
        f(item)(userOpt)(request)
      }
    }
  }
}

