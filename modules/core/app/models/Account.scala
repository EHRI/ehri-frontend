package models

import play.api.libs.json.{Json, JsValue, Writes}
import play.api.Plugin

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Account {
	def email: String
	def profile_id: String
  def password: Option[String] = None
  def updatePassword(hashed: String): Account
  def setPassword(data: String): Account
  def delete(): Boolean
}

object Account {
  implicit val userWrites: Writes[Account] = new Writes[Account] {
    def writes(user: Account): JsValue = Json.obj(
      "email" -> user.email,
      "profile_id" -> user.profile_id
    )
  }
}

trait AccountDAO extends Plugin {
	def findByProfileId(id: String): Option[Account]
  def findByEmail(email: String): Option[Account]
  def create(email: String, profile_id: String): Option[Account]

}