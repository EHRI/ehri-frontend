package models.sql

import language.postfixOps
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._
import models.{Account,AccountDAO}


// -- Users

case class PersonaAccount(profile_id: String, email: String) extends Account {
  def delete(): Boolean = DB.withConnection { implicit connection =>
    val res: Int = SQL(
      """DELETE FROM users WHERE profile_id = {profile_id}""").on('profile_id -> profile_id).executeUpdate()
    res == 1
  }

  // Unsupported operations
  def setPassword(data: String) = ???
  def updatePassword(data: String) = ???
}

object PersonaAccount extends AccountDAO {

  val simple = {
     get[String]("users.profile_id") ~ 
     get[String]("users.email") map {
       case profile_id ~ email => PersonaAccount(profile_id, email)
     }
  }

  def findAll: Seq[PersonaAccount] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users"""
    ).as(PersonaAccount.simple *)
  }

  def findByProfileId(profile_id: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE profile_id = {profile_id}
      """
    ).on('profile_id -> profile_id).as(PersonaAccount.simple.singleOpt)
  }
  
  def findByEmail(email: String): Option[PersonaAccount] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE email = {email}
      """
    ).on('email -> email).as(PersonaAccount.simple.singleOpt)
  } 

  def create(email: String, profile_id: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (profile_id, email) VALUES ({profile_id},{email})"""
    ).on('profile_id -> profile_id, 'email -> email).executeUpdate
    findByProfileId(profile_id)
  }
}

class PersonaAccountDAOPlugin(app: play.api.Application) extends AccountDAO {
  def findByProfileId(profile_id: String) = PersonaAccount.findByProfileId(profile_id)
  def findByEmail(email: String) = PersonaAccount.findByEmail(email)
  def create(email: String, profile_id: String) = PersonaAccount.create(email, profile_id)
}
