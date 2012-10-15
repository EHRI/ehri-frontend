package models.sql

import play.api._
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.util.Date

// -- Users

sealed trait Permission
case object Administrator extends Permission
case object NormalUser extends Permission


case class User(profile_id: String, email: String, profile: Option[models.UserProfile] = None) {  
  def withProfile(prof: Option[models.UserProfile]) = copy(profile=prof)
}

object User {

  val simple = {
     get[String]("users.profile_id") ~ 
     get[String]("users.email") map {
       case profile_id ~ email => User(profile_id, email)
     }
  }

  def findAll: Seq[User] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users"""
    ).as(User.simple *)
  }

  def authenticate(profile_id: String, email: String): User = DB.withConnection { implicit connection =>
    SQL(
      """
        INSERT INTO users (profile_id,email) VALUES ({profile_id},{email})
        ON DUPLICATE KEY UPDATE email = {email}
      """
    ).on('profile_id -> profile_id, 'email -> email).executeUpdate()
    User(profile_id, email)
  }

  def authenticate(email: String): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE email = {email}
      """
    ).on('email -> email).as(User.simple.singleOpt)
  }

  def create(profile_id: String, email: String): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (profile_id, email) VALUES ({profile_id},{email})"""
    ).on('profile_id -> profile_id, 'email -> email).executeUpdate
    authenticate(email)
  }
}
