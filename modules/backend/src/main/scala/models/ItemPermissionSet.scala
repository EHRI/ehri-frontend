package models

import play.api.libs.json._


object ItemPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = Seq[(String, Seq[PermissionType.Value])]
  type PermDataRaw = Seq[Map[String, Seq[String]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   */
  implicit def _reads(contentType: ContentTypes.Value): Reads[ItemPermissionSet] = Reads.seq(Reads.map(Reads.seq[String])).map { pd =>
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
}
