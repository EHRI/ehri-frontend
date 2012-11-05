package models.sql

import play.api.Plugin
import models.UserProfile
import models.PermissionSet

trait User {
	def email: String
	def permissions: Option[PermissionSet[UserProfile]]
	def profile_id: String
	def profile: Option[models.UserProfile]	
	def withProfile(p: UserProfile): User
	def withPermissions(p: PermissionSet[UserProfile]): User
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}