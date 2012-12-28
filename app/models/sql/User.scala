package models.sql

import play.api.Plugin

trait User {
	def email: String
	def profile_id: String
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}