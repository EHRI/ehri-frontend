package models.sql

import play.api._
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.util.Date

// -- Users

case class OpenIDUser(id: Long, email: String, profile_id: String, profile: Option[models.UserProfile] = None) extends User {

  lazy val associations: Seq[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association 
        join openid_user on openid_association.user_id = openid_user.id 
        where openid_user.id = {id} 
      """
    ).on('id -> id).as(OpenIDAssociation.withUser *)
  }

  def addAssociation(assoc: String): OpenIDUser = DB.withConnection { implicit connection =>
    val res = SQL(
      """
        INSERT INTO openid_association (id, user_id, openid_url) VALUES (DEFAULT, {user_id},{url})
      """
    ).on('user_id -> id, 'url -> assoc).executeInsert()
    println("Added association! " + res)
    this
  }

  def withProfile(prof: models.UserProfile) = copy(profile = Some(prof))

  def isStaff = false // STUB
}

object OpenIDUser {

  val simple = {
    get[Long]("openid_user.id") ~
      get[String]("openid_user.email") ~
      get[String]("openid_user.profile_id") map {
        case id ~ email ~ profile_id => OpenIDUser(id, email, profile_id)
      }
  }

  def findAll: Seq[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """select * from openid_user"""
    ).as(OpenIDUser.simple *)
  }

  def authenticate(id: Long, email: String, profile_id: String): OpenIDUser = DB.withConnection { implicit connection =>
    SQL(
      """
        INSERT INTO openid_user (id,email) VALUES ({id},{email},{profile_id})
        ON DUPLICATE KEY UPDATE id = {id}
      """
    ).on('id -> id, 'email -> email, 'profile_id -> profile_id).executeUpdate()
    OpenIDUser(id, email, profile_id)
  }

  def authenticate(url: String): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM openid_user 
          JOIN openid_association ON openid_association.user_id = openid_user.id
          WHERE openid_association.openid_url = {url}
      """
    ).on('url -> url).as(OpenIDUser.simple.singleOpt)
  }

  def findById(id: Long): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_user where id = {id}
      """
    ).on('id -> id).as(OpenIDUser.simple.singleOpt)
  }

  def findByEmail(email: String): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_user where email = {email}
      """
    ).on('email -> email).as(OpenIDUser.simple.singleOpt)
  }

  def findByProfileId(id: String): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_user where profile_id = {id}
      """
    ).on('id -> id).as(OpenIDUser.simple.singleOpt)
  }

  def create(email: String, profile_id: String): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO openid_user (id, email, profile_id) VALUES (DEFAULT,{email},{profile_id})"""
    ).on('email -> email, 'profile_id -> profile_id).executeUpdate
    SQL(
      """SELECT * FROM openid_user WHERE id = currval('openid_user_id_seq')"""
    ).as(OpenIDUser.simple.singleOpt)
  }
}

// -- Associations

case class OpenIDAssociation(id: Long, userid: Long, url: String, user: Option[OpenIDUser] = None) {

  lazy val users: Seq[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_user 
        join openid_association on openid_association.user_id = openid_user.id
        where openid_association.id = {id}
      """
    ).on('id -> id).as(OpenIDUser.simple *)
  }
}

object OpenIDAssociation {

  val simple = {
    get[Long]("openid_association.id") ~
      get[Long]("openid_association.user_id") ~
      get[String]("openid_association.openid_url") map {
        case id ~ userid ~ url => OpenIDAssociation(id, userid, url, None)
      }
  }

  val withUser = {
    simple ~ OpenIDUser.simple map {
      case association ~ user => association.copy(user = Some(user))
    }
  }

  def findAll: Seq[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association join openid_user on openid_association.user_id =  openid_user.id
      """
    ).as(OpenIDAssociation.withUser *)
  }

  def findByUrl(url: String): Option[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association
          join openid_user on openid_association.user_id =  openid_user.id where
        openid_association.openid_url = {url} LIMIT 1
      """
    ).on('url -> url).as(OpenIDAssociation.withUser.singleOpt)
  }
}

