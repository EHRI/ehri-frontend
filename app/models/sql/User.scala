package models.sql

import play.api.Plugin

import models.UserProfile

trait User {
	def email: String
	def profile_id: String
	def profile: Option[models.UserProfile]	
	def withProfile(p: UserProfile): User
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]  
}