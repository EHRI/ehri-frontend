package models.sql

import language.postfixOps
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._
import models.{HashedPassword, Account, AccountDAO}


// -- Users

case class OpenIDAccount(id: String, email: String) extends Account with PasswordManager with TokenManager {

  lazy val associations: Seq[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association 
        join users on openid_association.user_id = users.id
        where users.id = {id}
      """
    ).on('id -> id).as(OpenIDAssociation.withUser *)
  }

  def addAssociation(assoc: String): OpenIDAccount = DB.withConnection { implicit connection =>
    val res = SQL(
      """
        INSERT INTO openid_association (id, openid_url) VALUES ({id},{url})
      """
    ).on('id -> id, 'url -> assoc).executeInsert()
    this
  }

  def delete(): Boolean = DB.withConnection { implicit connection =>
    val res: Int = SQL(
      """DELETE FROM users WHERE id = {id}""").on('id -> id).executeUpdate()
    res == 1
  }

  def isStaff = false // STUB
}

object OpenIDAccount extends AccountDAO {

  val simple = {
    get[String]("users.id") ~
    get[String]("users.email") map {
      case id ~ email => OpenIDAccount(id, email)
    }
  }

  def findAll: Seq[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL("select * from users").as(OpenIDAccount.simple *)
  }

  /**
   * Authenticate a user via an openid association.
   *
   * @param url
   * @return
   */
  def authenticate(url: String): Option[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users
          JOIN openid_association ON openid_association.id = users.id
          WHERE openid_association.openid_url = {url}
      """
    ).on('url -> url).as(OpenIDAccount.simple.singleOpt)
  }

  def findByEmail(email: String): Option[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where email = {email}
      """
    ).on('email -> email).as(OpenIDAccount.simple.singleOpt)
  }

  def findByProfileId(id: String): Option[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL("select * from users where id = {id}")
      .on('id -> id).as(OpenIDAccount.simple.singleOpt)
  }

  def create(id: String, email: String): Option[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (id, email) VALUES ({id},{email})"""
    ).on('id -> id, 'email -> email).executeUpdate
    findByProfileId(id)
  }

  def findByResetToken(token: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT u.*, t.token FROM users u, token t
         WHERE u.id = t.id AND t.token = {token}
          AND t.expires > NOW()"""
    ).on('token -> token).as(OpenIDAccount.simple.singleOpt)
  }
}

// -- Associations

case class OpenIDAssociation(id: String, url: String, user: Option[OpenIDAccount] = None) {

  lazy val users: Seq[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users
        join openid_association on openid_association.id = users.id
        where openid_association.id = {id}
      """
    ).on('id -> id).as(OpenIDAccount.simple *)
  }
}

object OpenIDAssociation {

  val simple = {
    get[String]("openid_association.id") ~
    get[String]("openid_association.openid_url") map {
      case id ~ url => OpenIDAssociation(id, url, None)
    }
  }

  val withUser = {
    simple ~ OpenIDAccount.simple map {
      case association ~ user => association.copy(user = Some(user))
    }
  }

  def findAll: Seq[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association join users on openid_association.id =  users.id
      """
    ).as(OpenIDAssociation.withUser *)
  }

  def findByUrl(url: String): Option[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association
          join users on openid_association.id =  users.id where
        openid_association.openid_url = {url} LIMIT 1
      """
    ).on('url -> url).as(OpenIDAssociation.withUser.singleOpt)
  }
}

class OpenIDAccountDAOPlugin(app: play.api.Application) extends AccountDAO {
  def findByProfileId(id: String) = OpenIDAccount.findByProfileId(id)
  def findByEmail(email: String) = OpenIDAccount.findByEmail(email)
  def findByResetToken(token: String) = OpenIDAccount.findByResetToken(token)
  def create(id: String, email: String) = OpenIDAccount.create(id, email)
}

