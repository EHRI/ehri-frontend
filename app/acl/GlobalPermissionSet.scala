package acl

import play.api.libs.json._
import defines._
import models.base.Accessor
import defines.PermissionType.Annotate
import defines.PermissionType.Create
import defines.PermissionType.Delete
import defines.PermissionType.Owner
import defines.PermissionType.Update
import scala.Option.option2Iterable

object GlobalPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = List[(String, Map[ContentType.Value, List[PermissionType.Value]])]
  type PermDataRaw = List[Map[String, Map[String, List[String]]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   * 
   * NB: The Map[String, Map[String, List[String]]] items in each list are
   * only maps to make them a homogeneous data set, and there should be only
   * one key/value per map, hence we flatMap through them taking only the first
   * value in each, converting them to a tuple for internal use.
   *
   */
  private def extract(pd: PermDataRaw): PermData = {
    pd.flatMap { pmap =>
      pmap.headOption.map { case (user, perms) =>
		(user, perms.map {
		  case (et, plist) =>
		    try {
		      (ContentType.withName(et), plist.map(PermissionType.withName(_)))
		    } catch {
		      case e: NoSuchElementException =>
		      // If we get an expected permission, fail fast!
		    sys.error("Unable to extract permissions: Entity: '%s', elements: %s".format(et, plist))
		  }
		})
      }
    }
  }

  /**
   * Construct a new global permission set from a JSON value.
   */
  def apply[T <: Accessor](accessor: T, json: JsValue): GlobalPermissionSet[T] = json.validate[List[Map[String, Map[String, List[String]]]]].fold(
    valid = { pd => new GlobalPermissionSet(accessor, extract(pd)) },
    invalid = { e => sys.error(e.toString) }
  )
}

/**
 * Global permissions granted to either a UserProfileF or a GroupF.
 */
case class GlobalPermissionSet[+T <: Accessor](val user: T, val data: GlobalPermissionSet.PermData)
  extends PermissionSet {

  /**
   * Check if this permission set has the given permission.
   */
  def has(sub: ContentType.Value, permission: PermissionType.Value): Boolean =
    !data.flatMap(_._2.get(sub)).filter( plist => plist.exists( p =>
        PermissionType.in(p, permission))).isEmpty

  /**
   * Get the permission grant for a given permission (if any), which contains
   * the accessor to whom the permission was granted.
   */
  def get(contentType: ContentType.Value, permission: PermissionType.Value): Option[Permission[T]] = {
    val accessors = data.flatMap {
      case (user, perms) =>
        perms.get(contentType).flatMap { permSet =>
          if (permSet.exists(p => PermissionType.in(p, permission))) Some((user, permission))
          else None
        }
    }
    // If we have one or more accessors, then we are going to
    // grant the permission, but we need to search the list
    // to determine the accessor who was granted the permissions.
    accessors.headOption.map {
      case (userId, perm) =>
        if (user.id == userId) Permission(perm)
        else {
          user.getAccessor(user.groups, userId) match {
            case Some(u) if u.id == user.id => Permission(perm)
            case s @ Some(u) => Permission(perm, s)
            case x => Permission(perm)
          }
        }
    }
  }
}
