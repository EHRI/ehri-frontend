package models.sql

trait User {
	def email: String
	def profile: Option[models.UserProfile]
}