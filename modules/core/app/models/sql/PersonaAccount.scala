package models.sql

import language.postfixOps
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._
import models.{HashedPassword, Account, AccountDAO}


// -- Users

case class PersonaAccount(id: String, email: String) extends Account with PasswordManager with TokenManager {
  def delete(): Boolean = DB.withConnection { implicit connection =>
    val res: Int = SQL(
      """DELETE FROM users WHERE id = {id}""").on('id -> id).executeUpdate()
    res == 1
  }
}

object PersonaAccount extends AccountDAO {

  val simple = {
     get[String]("users.id") ~
     get[String]("users.email") map {
       case profile_id ~ email => PersonaAccount(profile_id, email)
     }
  }

  def findAll: Seq[PersonaAccount] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users"""
    ).as(PersonaAccount.simple *)
  }

  def findByProfileId(id: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE id = {id}
      """
    ).on('id -> id).as(PersonaAccount.simple.singleOpt)
  }
  
  def findByEmail(email: String): Option[PersonaAccount] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users WHERE email = {email}
      """
    ).on('email -> email).as(PersonaAccount.simple.singleOpt)
  } 

  def create(id: String, email: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (id, email) VALUES ({id},{email})"""
    ).on('id -> id, 'email -> email).executeUpdate
    findByProfileId(id)
  }

  def findByResetToken(token: String): Option[Account] = DB.withConnection { implicit connection =>
    ???
  }
}

class PersonaAccountDAOPlugin(app: play.api.Application) extends AccountDAO {
  def findByProfileId(id: String) = PersonaAccount.findByProfileId(id)
  def findByEmail(email: String) = PersonaAccount.findByEmail(email)
  def findByResetToken(token: String) = PersonaAccount.findByResetToken(token)
  def create(id: String, email: String) = PersonaAccount.create(id, email)
}
