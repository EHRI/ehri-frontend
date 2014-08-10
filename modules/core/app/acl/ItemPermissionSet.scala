package acl

import play.api.libs.json._
import defines._
import models.base.Accessor


object ItemPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = Seq[(String, Seq[PermissionType.Value])]
  type PermDataRaw = Seq[Map[String, Seq[String]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   */
  implicit def restReads(contentType: ContentTypes.Value): Reads[ItemPermissionSet] = Reads.seq(Reads.map(Reads.seq[String])).map { pd =>
    // Raw data is a Seq[Map[String, Seq[String]]]
    import scala.util.control.Exception._
    pd.flatMap { userPermMap =>
      userPermMap.headOption.map { case (userId, plist) =>
        val perms = plist.flatMap { ps =>
          catching(classOf[NoSuchElementException])
            .opt(PermissionType.withName(ps))
        }
        userId -> perms
      }
    }
  }.map(valid => ItemPermissionSet(contentType, valid))
}

/**
 * Item-level permissions granted to either a UserProfileF or a GroupF.
 */
case class ItemPermissionSet(contentType: ContentTypes.Value, data: ItemPermissionSet.PermData) extends PermissionSet {

  /**
   * Check if this permission set has the given permission.
   */
  def has(permission: PermissionType.Value): Boolean =
      data.flatMap(t => t._2).exists(p => PermissionType.in(p, permission))

  /**
   * Get the permission grant for a given permission (if any), which contains
   * the accessor to whom the permission was granted.
   */  
  def get[T <: Accessor](user: Accessor, permission: PermissionType.Value): Option[Permission[T]] = {
    val accessors = data.flatMap { case (uid, perms) =>
        if (perms.exists(p => PermissionType.in(p, permission))) Some((uid, permission))
        else None
    }
    accessors.headOption.map {
      case (userId, perm) =>
        if (user.id == userId) Permission(perm)
        else user.getAccessor(user.groups, userId) match {
          case Some(u) if u.id == user.id => Permission(perm)
          case s @ Some(u) => Permission(perm, s)
          case x => Permission(perm)
        }
    }
  }
}
