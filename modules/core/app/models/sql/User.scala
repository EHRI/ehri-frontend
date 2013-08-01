package models.sql

import play.api.Plugin
import play.api.libs.json.{JsValue, Json, Writes}

object User {
  implicit val userWrites: Writes[User] = new Writes[User] {
    def writes(user: User): JsValue = Json.obj(
      "email" -> user.email,
      "profile_id" -> user.profile_id
    )
  }
}

trait User {
	def email: String
	def profile_id: String
  def password: Option[String] = None
  def updatePassword(hashed: String): User
  def setPassword(data: String): User
  def delete(): Boolean
}

trait UserDAO extends Plugin {
	def findByProfileId(id: String): Option[User]
  def findByEmail(email: String): Option[User]
  def create(email: String, profile_id: String): Option[User]

}