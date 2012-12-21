package models.sql

import defines._
import play.api.Plugin
import models.UserProfile
import acl._
import models.UserProfileRepr

trait User {
	def email: String
	def profile_id: String
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}