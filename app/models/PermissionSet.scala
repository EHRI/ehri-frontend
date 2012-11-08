package models

import play.api.libs.json._

import defines._

object PermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = List[Map[String, Map[ContentType.Value, List[PermissionType.Value]]]]
  type PermDataRaw = List[Map[String, Map[String, List[String]]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   */
  def extract(pd: PermDataRaw): PermData = {
    pd.map { pmap =>
      pmap.mapValues { perms =>
        perms.map {
          case (et, plist) =>
            try {
              (ContentType.withName(et), plist.map(PermissionType.withName(_)))
            } catch {
              case e: NoSuchElementException =>
                // If we get an expected permission, fail fast!
                sys.error("Unable to extract permissions: Entity: '%s', elements: %s".format(et, plist))
            }

        }
      }
    }
  }

  def apply[T <: Accessor](acc: T, json: JsValue) = json.validate[PermDataRaw].fold(
    valid = { pd => new PermissionSet(acc, extract(pd)) },
    invalid = { e => sys.error(e.toString) }
  )
}

/**
 * Search
 */
case class PermissionSet[+T <: Accessor](val user: T, val data: PermissionSet.PermData) {

  def get(sub: ContentType.Value, perm: PermissionType.Value): Option[PermissionGrant[T]] = {
    val accessors = data.flatMap { pm =>
      pm.headOption.flatMap {
        case (user, perms) =>
          perms.get(sub).flatMap { permSet =>
            if (permSet.contains(perm)) Some((user, perm))
            else None
          }
      }
    }
    accessors.headOption.map {
      case (userId, perm) =>
        if (user.identifier == userId) PermissionGrant(perm)
        else user.getAccessor(user.groups, userId) match {
          case Some(u) if u.identifier == user.identifier => PermissionGrant(perm)
          case s @ Some(u) => PermissionGrant(perm, s)
          case x => PermissionGrant(perm)
        }
    }
  }
}

case class PermissionGrant[+T <: Accessor](
  val permission: PermissionType.Value,
  val inheritedFrom: Option[Accessor] = None) {
  override def toString = inheritedFrom match {
    case Some(accessor) => "%s (from %s)".format(permission, accessor.identifier)
    case None => permission.toString
  }
}