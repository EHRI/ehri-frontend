package models

import play.api.libs.json._

object PermissionSet {

  // Type alias for the very verbose permission-set data structure.
  type PermData = List[Map[String, Map[String, List[String]]]]

  def apply[T <: Accessor[Group]](acc: T, json: JsValue) = json.validate[PermData].fold(
    valid = { pd => new PermissionSet(acc, pd) },
    invalid = { e => sys.error(e.toString) }
  )
}

/**
 * Search
 */
case class PermissionSet[T <: Accessor[Group]](val user: T, val data: PermissionSet.PermData) {

  def get(sub: String, perm: String): Option[PermissionGrant[T]] = {
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

case class PermissionGrant[T <: Accessor[Group]](val permission: String, val inheritedFrom: Option[Accessor[Group]] = None) {
  override def toString = inheritedFrom match {
    case Some(accessor) => "%s (from %s)".format(permission, accessor.identifier)
    case None => permission
  }
}