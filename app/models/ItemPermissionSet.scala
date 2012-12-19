package models

import play.api.libs.json._
import defines._
import models.base.Accessor

object ItemPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = List[(String, List[PermissionType.Value])]
  type PermDataRaw = List[Map[String, List[String]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   */
  def extract(pd: PermDataRaw): PermData = {
    pd.flatMap { userPermMap =>
      userPermMap.headOption.map { case (userId, plist) =>
        try {
          List((userId, plist.map(PermissionType.withName(_))))
        } catch {
          case e: NoSuchElementException =>
                // If we get an expected permission, fail fast!
                sys.error("Unable to extract permissions: elements: %s".format(plist))          
        }
      }.getOrElse(Nil)
    }
  }

  def apply[T <: Accessor](acc: T, json: JsValue) = json.validate[PermDataRaw].fold(
    valid = { pd => new ItemPermissionSet(acc, extract(pd)) },
    invalid = { e => sys.error(e.toString) }
  )
}

/**
 * Search
 */
case class ItemPermissionSet[+T <: Accessor](val user: T, val data: ItemPermissionSet.PermData) {

  def has(perm: PermissionType.Value): Boolean = data.flatMap(t => t._2).contains(perm)

  def get(perm: PermissionType.Value): Option[PermissionGrant[T]] = {
    val accessors = data.flatMap { case (uid, perms) =>
        if (perms.contains(perm)) Some((uid, perm))
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
