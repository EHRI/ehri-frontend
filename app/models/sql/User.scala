package models.sql

import play.api.Plugin
import models.UserProfile
import models.PermissionSet
import models.UserProfileRepr

trait User {
	def email: String
	def permissions: Option[PermissionSet[UserProfileRepr]]
	def profile_id: String
	def profile: Option[models.UserProfileRepr]	
	def withProfile(p: UserProfileRepr): User
	def withPermissions(p: PermissionSet[UserProfileRepr]): User
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}