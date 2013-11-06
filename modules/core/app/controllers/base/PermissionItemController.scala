package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models.{PermissionGrant, UserProfile}
import models.json.RestReadable
import utils.PageParams

/**
 * Trait for setting permissions on an individual item.
 *
 * @tparam MT the entity's meta class
 */
trait PermissionItemController[MT] extends EntityRead[MT] {

  def manageItemPermissionsAction(id: String)(
      f: MT => rest.Page[PermissionGrant] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        val params = PageParams.fromRequest(request)
        for {
          permGrantsOrErr <- rest.PermissionDAO().listForItem(id, params)
        } yield {
          for { permGrants <- permGrantsOrErr.right } yield {
            f(item)(permGrants)(userOpt)(request)
          }
        }
      }
    }
  }

  def addItemPermissionsAction(id: String)(
      f: MT => Seq[(String,String)] => Seq[(String,String)] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {
          users <- rest.RestHelpers.getUserList
          groups <- rest.RestHelpers.getGroupList
        } yield {
          Right(f(item)(users)(groups)(userOpt)(request))
        }
      }
    }
  }


  def setItemPermissionsAction(id: String, userType: String, userId: String)(
      f: MT => Accessor => acl.ItemPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      AsyncRest {
        for {

          userOrErr <- rest.EntityDAO(EntityType.withName(userType)).get[Accessor](userId)
          // FIXME: Faking user for fetching perms to avoid blocking.
          // This means that when we have both the perm set and the userOpt
          // we need to re-assemble them so that the permission set has
          // access to a userOpt's groups to understand inheritance.
          permsOrErr <- rest.PermissionDAO().getItem(userOrErr.right.get, contentType, id)
        } yield {
          for {  accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
            f(item)(accessor)(perms.copy(user=accessor))(userOpt)(request)
          }
        }
      }
    }
  }

  def setItemPermissionsPostAction(id: String, userType: String, userId: String)(
      f: acl.ItemPermissionSet[Accessor] => Option[UserProfile] => Request[AnyContent] => Result)(
      implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Grant, contentType) { item => implicit userOpt => implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: List[String] = data.get(contentType.toString).map(_.toList).getOrElse(List())
      implicit val accessorConverter = Accessor.Converter
      getEntity[Accessor](EntityType.withName(userType), userId) { accessor =>
        AsyncRest {
          rest.PermissionDAO().setItem(accessor, contentType, id, perms).map { permsOrErr =>
            permsOrErr.right.map { perms =>
              f(perms)(userOpt)(request)
            }
          }
        }
      }
    }
  }
}

