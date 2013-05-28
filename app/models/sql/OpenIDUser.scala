package models.sql

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._


// -- Users

case class OpenIDUser(id: Long, email: String, profile_id: String) extends User {

  lazy val associations: Seq[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association 
        join users on openid_association.user_id = users.id
        where users.id = {id}
      """
    ).on('id -> id).as(OpenIDAssociation.withUser *)
  }

  def addAssociation(assoc: String): OpenIDUser = DB.withConnection { implicit connection =>
    val res = SQL(
      """
        INSERT INTO openid_association (id, user_id, openid_url) VALUES (DEFAULT, {user_id},{url})
      """
    ).on('user_id -> id, 'url -> assoc).executeInsert()
    this
  }

  override def password: Option[String] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT data FROM user_auth WHERE user_auth.id = {id} LIMIT 1"""
    ).on('id -> id).as(str("data").singleOpt)

  }

  def setPassword(data: String): OpenIDUser = DB.withConnection{ implicit connection =>
    val res = SQL(
      """
        INSERT INTO user_auth (id, data) VALUES ({id},{data})
      """
    ).on('id -> id, 'data -> data).executeInsert()
    this
  }

  def updatePassword(data: String): OpenIDUser = DB.withConnection{ implicit connection =>
    val res = SQL(
      """
        UPDATE user_auth SET data={data} WHERE id={id}
      """
    ).on('id -> id, 'data -> data).executeUpdate()
    this
  }

  def delete(): Boolean = DB.withConnection { implicit connection =>
    val res: Int = SQL(
      """DELETE FROM users WHERE id = {id}""").on('id -> id).executeUpdate()
    res == 1
  }

  def isStaff = false // STUB
}

object OpenIDUser extends UserDAO {

  val simple = {
    get[Long]("users.id") ~
      get[String]("users.email") ~
      get[String]("users.profile_id") map {
        case id ~ email ~ profile_id => OpenIDUser(id, email, profile_id)
      }
  }

  def findAll: Seq[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users"""
    ).as(OpenIDUser.simple *)
  }

  /**
   * Authenticate a user via an openid association.
   *
   * @param url
   * @return
   */
  def authenticate(url: String): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """
        SELECT * FROM users
          JOIN openid_association ON openid_association.user_id = users.id
          WHERE openid_association.openid_url = {url}
      """
    ).on('url -> url).as(OpenIDUser.simple.singleOpt)
  }

  /**
   * Authenticate a user via an email and a password stored in the user_auth table.
   *
   * @param email
   * @param data
   * @return
   */
  def authenticate(email: String, data: String) = DB.withConnection{ implicit connection =>
    SQL(
      """
        SELECT * FROM users
          JOIN user_auth ON user_auth.id = users.id
          WHERE users.email = {email} AND user_auth.data = {data}
      """
    ).on('email -> email, 'data -> data).as(OpenIDUser.simple.singleOpt)
  }

  def findById(id: Long): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where id = {id}
      """
    ).on('id -> id).as(OpenIDUser.simple.singleOpt)
  }

  def findByEmail(email: String): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where email = {email}
      """
    ).on('email -> email).as(OpenIDUser.simple.singleOpt)
  }

  def findByProfileId(id: String): Option[User] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where profile_id = {id}
      """
    ).on('id -> id).as(OpenIDUser.simple.singleOpt)
  }

  def create(email: String, profile_id: String): Option[OpenIDUser] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (id, email, profile_id) VALUES (DEFAULT,{email},{profile_id})"""
    ).on('email -> email, 'profile_id -> profile_id).executeUpdate
    SQL(
      """SELECT * FROM users WHERE id = currval('users_id_seq')"""
    ).as(OpenIDUser.simple.singleOpt)
  }
}

// -- Associations

case class OpenIDAssociation(id: Long, userid: Long, url: String, user: Option[OpenIDUser] = None) {

  lazy val users: Seq[User] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users
        join openid_association on openid_association.user_id = users.id
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
        select * from openid_association join users on openid_association.user_id =  users.id
      """
    ).as(OpenIDAssociation.withUser *)
  }

  def findByUrl(url: String): Option[OpenIDAssociation] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association
          join users on openid_association.user_id =  users.id where
        openid_association.openid_url = {url} LIMIT 1
      """
    ).on('url -> url).as(OpenIDAssociation.withUser.singleOpt)
  }
}

class OpenIDUserDAOPlugin(app: play.api.Application) extends UserDAO {
  def findByProfileId(profile_id: String) = OpenIDUser.findByProfileId(profile_id)
}

