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
	    pm.headOption.flatMap { case (user, perms) =>
	      perms.get(sub).flatMap { permSet =>
	        if (permSet.contains(perm)) Some(user)
	        else None
	      }
	    }
	  }
	  accessors.headOption.map { userId =>
	    user.getAccessor(userId) match {
	      case Some(u) if u.identifier == user.id => PermissionGrant()
	      case s@Some(u) => PermissionGrant(s)
	      case _ => PermissionGrant()
	    }
	  }
	}
}

case class PermissionGrant(val inheritedFrom: Option[Accessor[Group]] = None) {
  
}