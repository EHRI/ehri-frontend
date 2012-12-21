package models.sql

import defines._
import play.api.Plugin
import models.UserProfile
import acl._
import models.UserProfileRepr

trait User {
	def email: String
	def profile_id: String
	def profile: Option[models.UserProfileRepr]	
	def globalPermissions: Option[GlobalPermissionSet[UserProfileRepr]]
	def itemPermissions: Option[ItemPermissionSet[UserProfileRepr]]

	def withProfile(p: UserProfileRepr): User
	def withGlobalPermissions(p: GlobalPermissionSet[UserProfileRepr]): User
	def withItemPermissions(p: ItemPermissionSet[UserProfileRepr]): User

	def hasPermission(p: PermissionType.Value)(implicit ct: ContentType.Value): Boolean = {
	  globalPermissions.map { gp =>
	  	if (gp.has(ct, p)) true
	  	else {
	  		itemPermissions.map { ip =>
	  		  ip.has(p)
	  		}.getOrElse(false)
	  	}
	  }.getOrElse(false)
	}
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}