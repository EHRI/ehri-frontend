package models

import play.api.libs.json._

object PermissionSet {

  // Type alias for the very verbose permission-set data structure.
  type PermData = List[Map[String, Map[String, List[String]]]]

  def apply(user: UserProfile, json: JsValue): PermissionSet = {
    json.validate[PermData].fold(
      valid = { pd => new PermissionSet(user, pd) },
      invalid = { e => sys.error(e.toString) }
    )
  }
}

/**
 * Search
 */
case class PermissionSet(val user: UserProfile, val data: PermissionSet.PermData) {
  def get(sub: String, perm: String): Option[PermissionGrant] = {
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
        user.getAccessor(userId) match {
          case Some(u) if u.identifier == user.id => PermissionGrant(perm)
          case s @ Some(u) => PermissionGrant(perm, s)
          case x => PermissionGrant(perm)
        }
    }
  }
}

case class PermissionGrant(val permission: String, val inheritedFrom: Option[Accessor[Group]] = None) {
  override def toString = inheritedFrom match {
    case Some(accessor) => "%s (from %s)".format(permission, accessor.identifier)
    case None => permission
  }
}