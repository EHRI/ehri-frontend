package models.sql

import play.api._
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.util.Date

// -- Users

case class PersonaUser(profile_id: String, email: String, profile: Option[models.UserProfile] = None) extends User {  
  def withProfile(prof: Option[models.UserProfile]) = copy(profile=prof)
}

object PersonaUser {

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

  def authenticate(profile_id: String, email: String): PersonaUser = DB.withConnection { implicit connection =>
    SQL(
      """
        INSERT INTO users (profile_id,email) VALUES ({profile_id},{email})
        ON DUPLICATE KEY UPDATE email = {email}
      """
    ).on('profile_id -> profile_id, 'email -> email).executeUpdate()
    PersonaUser(profile_id, email)
  }

  def authenticate(email: String): Option[PersonaUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE email = {email}
      """
    ).on('email -> email).as(PersonaUser.simple.singleOpt)
  }

  def create(profile_id: String, email: String): Option[PersonaUser] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (profile_id, email) VALUES ({profile_id},{email})"""
    ).on('profile_id -> profile_id, 'email -> email).executeUpdate
    authenticate(email)
  }
}
