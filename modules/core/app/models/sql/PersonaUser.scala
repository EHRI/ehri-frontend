package models.sql

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._


// -- Users

case class PersonaUser(profile_id: String, email: String) extends User {
  def delete(): Boolean = DB.withConnection { implicit connection =>
    val res: Int = SQL(
      """DELETE FROM users WHERE profile_id = {profile_id}""").on('profile_id -> profile_id).executeUpdate()
    res == 1
  }

  // Unsupported operations
  def setPassword(data: String) = ???
  def updatePassword(data: String) = ???
}

object PersonaUser extends UserDAO {

  val simple = {
     get[String]("users.profile_id") ~ 
     get[String]("users.email") map {
       case profile_id ~ email => PersonaUser(profile_id, email)
     }
  }

  def findAll: Seq[PersonaUser] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users"""
    ).as(PersonaUser.simple *)
  }

  def findByProfileId(profile_id: String): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE profile_id = {profile_id}
      """
    ).on('profile_id -> profile_id).as(PersonaUser.simple.singleOpt)
  }
  
  def findByEmail(email: String): Option[PersonaUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE email = {email}
      """
    ).on('email -> email).as(PersonaUser.simple.singleOpt)
  } 

  def create(email: String, profile_id: String): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (profile_id, email) VALUES ({profile_id},{email})"""
    ).on('profile_id -> profile_id, 'email -> email).executeUpdate
    findByProfileId(profile_id)
  }
}

class PersonaUserDAOPlugin(app: play.api.Application) extends UserDAO {
  def findByProfileId(profile_id: String) = PersonaUser.findByProfileId(profile_id)
  def findByEmail(email: String) = PersonaUser.findByEmail(email)
  def create(email: String, profile_id: String) = PersonaUser.create(email, profile_id)
}
