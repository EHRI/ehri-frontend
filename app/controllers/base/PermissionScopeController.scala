package controllers.base

import play.api.mvc.{Call,RequestHeader}
import play.api.libs.concurrent.execution.defaultContext
import models.base._
import models.base.Persistable
import defines._
import models.UserProfile
import acl.GlobalPermissionSet

/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam T the entity's build class
 */
trait PermissionScopeController[T <: AccessibleEntity] extends EntityRead[T] {

  val targetContentTypes: Seq[ContentType.Value]

  val permissionScopeAction: (String, String, String) => Call
  val setPermissionScopeAction: (String, String, String) => Call

  /**
   * The visibility view takes an Accessor object, a list of id->name groups tuples,
   * a list of id->name user tuples, an action to redirect to when complete, a
   * UserProfile, and a request.
   */
  type PermissionScopeViewType = (AccessibleEntity, Accessor, GlobalPermissionSet[models.base.Accessor],
        Seq[ContentType.Value], Call, UserProfile, RequestHeader) => play.api.templates.Html

  val permissionScopeView: PermissionScopeViewType

  def permissionScope(id: String, userType: String, userId: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          userOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
          permsOrErr <- rest.PermissionDAO(user)
              .getScope(Accessor(userOrErr.right.get), itemOrErr.right.get.id)
        } yield {
          for { item <- itemOrErr.right ; accessor <- userOrErr.right; perms <- permsOrErr.right } yield {
            println("PERMS: " + perms)
            Ok(permissionScopeView(builder(item),  Accessor(accessor), perms, targetContentTypes,
              setPermissionScopeAction(id, userType, userId), user, request))
          }
        }
      }
  }

  def permissionScopePost(id: String, userType: String, userId: String) = withItemPermission(id, PermissionType.Grant, contentType) { implicit user =>
    implicit request =>
      implicit val maybeUser = Some(user)
      val data = request.body.asFormUrlEncoded.getOrElse(Map())
      val perms: Map[String, List[String]] = targetContentTypes.map { ct =>
        (ct.toString, data.get(ct.toString).map(_.toList).getOrElse(List()))
      }.toMap

      AsyncRest {
        for {
          itemOrErr <- rest.EntityDAO(entityType, maybeUser).get(id)
          accessorOrErr <- rest.EntityDAO(EntityType.withName(userType), maybeUser).get(userId)
        } yield {
          for { item <- itemOrErr.right; accessor <- accessorOrErr.right} yield {
            AsyncRest {
              rest.PermissionDAO(user).setScope(Accessor(accessor), item.id, perms).map { boolOrErr =>
                boolOrErr.right.map { bool =>
                  Redirect(showAction(id))
                }
              }
            }
          }
        }
      }
  }
}

