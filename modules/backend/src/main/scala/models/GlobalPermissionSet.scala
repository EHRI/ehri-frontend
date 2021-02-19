package models

import play.api.libs.json._

import scala.util.control.Exception._

object GlobalPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = Seq[(String, Map[ContentTypes.Value, Seq[PermissionType.Value]])]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less "stringly" typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   *
   * NB: The Map[String, Map[String, Seq[String]]] items in each list are
   * only maps to make them a homogeneous data set, and there should be only
   * one key/value per map, hence we flatMap through them taking only the first
   * value in each, converting them to a tuple for internal use.
   *
   */
  implicit val restReads: Reads[GlobalPermissionSet] = Reads.seq(Reads.map(Reads.map(Reads.seq[String]))).map { pd =>
    pd.flatMap { pmap =>
      pmap.headOption.map { case (user, perms) =>
        (user, perms.flatMap {
          case (et, plist) =>
            val perms = plist.flatMap { ps =>
              catching(classOf[NoSuchElementException])
                .opt(PermissionType.withName(ps))
            }
            catching(classOf[NoSuchElementException])
              .opt(ContentTypes.withName(et) -> perms)
        })
      }
    }
  }.map(valid => GlobalPermissionSet(valid))
}

/**
 * Global permissions granted to either a UserProfileF or a GroupF.
 */
case class GlobalPermissionSet(data: GlobalPermissionSet.PermData) extends PermissionSet {
  /**
   * Check if this permission set has the given permission.
   */
  def has(sub: ContentTypes.Value, permission: PermissionType.Value): Boolean =
    data.flatMap(_._2.get(sub)).filter(plist => plist.exists(p =>
      PermissionType.in(p, permission))).nonEmpty
}
