package acl

import play.api.libs.json._
import defines._
import models.base.Accessor
import scala.Option.option2Iterable

object ItemPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = List[(String, List[PermissionType.Value])]
  type PermDataRaw = List[Map[String, List[String]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   */
  private def extract(pd: PermDataRaw): PermData = {
    pd.flatMap { userPermMap =>
      userPermMap.headOption.flatMap { case (userId, plist) =>
        try {
          Some((userId, plist.map(PermissionType.withName(_))))
        } catch {
          case e: NoSuchElementException =>
                // If we get an expected permission, fail fast!
                sys.error("Unable to extract permissions: elements: %s".format(plist))          
        }
      }
    }
  }

  /**
   * Construct an item permission set from a JSON value.
   */
  def apply[T <: Accessor](accessor: T, json: JsValue) = json.validate[PermDataRaw].fold(
    valid = { pd => new ItemPermissionSet(accessor, extract(pd)) },
    invalid = { e => sys.error(e.toString) }
  )
}

/**
 * Item-level permissions granted to either a UserProfileF or a GroupF.
 */
case class ItemPermissionSet[+T <: Accessor](val user: T, val data: ItemPermissionSet.PermData)
	extends PermissionSet {

  /**
   * Check if this permission set has the given permission (hackily expanded
   * to account for owner permissions.
   */
  def has(permission: PermissionType.Value): Boolean = expandOwnerPerms(data.flatMap(t => t._2)).contains(permission)

  /**
   * Get the permission grant for a given permission (if any), which contains
   * the accessor to whom the permission was granted.
   */  
  def get(permission: PermissionType.Value): Option[PermissionGrant[T]] = {
    val accessors = data.flatMap { case (uid, perms) =>
        if (expandOwnerPerms(perms).contains(permission)) Some((uid, permission))
        else None
    }
    accessors.headOption.map {
      case (userId, perm) =>
        if (user.id == userId) PermissionGrant(perm)
        else user.getAccessor(user.groups, userId) match {
          case Some(u) if u.id == user.id => PermissionGrant(perm)
          case s @ Some(u) => PermissionGrant(perm, s)
          case x => PermissionGrant(perm)
        }
    }
  }
}
