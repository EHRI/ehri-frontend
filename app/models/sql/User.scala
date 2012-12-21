package models.sql

import play.api.Plugin
import models.UserProfile
import acl.GlobalPermissionSet
import models.UserProfileRepr

trait User {
	def email: String
	def permissions: Option[GlobalPermissionSet[UserProfileRepr]]
	def profile_id: String
	def profile: Option[models.UserProfileRepr]	
	def withProfile(p: UserProfileRepr): User
	def withPermissions(p: GlobalPermissionSet[UserProfileRepr]): User
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}