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
   */
  def extract(pd: PermDataRaw): PermData = {
    pd.flatMap { pmap =>
      pmap.headOption.map {
        case (user, perms) =>
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

  def apply[T <: Accessor](acc: T, json: JsValue) = json.validate[PermDataRaw].fold(
    valid = { pd => new GlobalPermissionSet(acc, extract(pd)) },
    invalid = { e => sys.error(e.toString) }
  )
}

/**
 * Search
 */
case class GlobalPermissionSet[+T <: Accessor](val user: T, val data: GlobalPermissionSet.PermData)
  extends PermissionSet {

  def has(sub: ContentType.Value, perm: PermissionType.Value): Boolean =
    !data.flatMap(_._2.get(sub)).filter(expandOwnerPerms(_).contains(perm)).isEmpty

  def get(sub: ContentType.Value, perm: PermissionType.Value): Option[PermissionGrant[T]] = {
    val accessors = data.flatMap {
      case (user, perms) =>
        perms.get(sub).flatMap { permSet =>
          if (expandOwnerPerms(permSet).contains(perm)) Some((user, perm))
          else None
        }
    }
    // If we have one or more accessors, then we are going to
    // grant the permission, but we need to search the list
    // to determine the accessor who was granted the permissions.
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
