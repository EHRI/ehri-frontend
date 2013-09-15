package models.sql

import language.postfixOps
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._
import models.{Account,AccountDAO}
import java.util.UUID


// -- Users

case class OpenIDAccount(id: Long, email: String, profile_id: String) extends Account {

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

  def setPassword(data: String): OpenIDAccount = DB.withConnection{ implicit connection =>
    val res = SQL(
      """
        INSERT INTO user_auth (id, data) VALUES ({id},{data})
      """
    ).on('id -> id, 'data -> data).executeInsert()
    this
  }

  def updatePassword(data: String): OpenIDAccount = DB.withConnection{ implicit connection =>
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

object OpenIDAccount extends AccountDAO {

  val simple = {
    get[Long]("users.id") ~
      get[String]("users.email") ~
      get[String]("users.profile_id") map {
        case id ~ email ~ profile_id => OpenIDAccount(id, email, profile_id)
      }
  }

  def findAll: Seq[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users"""
    ).as(OpenIDAccount.simple *)
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
          JOIN openid_association ON openid_association.user_id = users.id
          WHERE openid_association.openid_url = {url}
      """
    ).on('url -> url).as(OpenIDAccount.simple.singleOpt)
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
    ).on('email -> email, 'data -> data).as(OpenIDAccount.simple.singleOpt)
  }

  def findById(id: Long): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where id = {id}
      """
    ).on('id -> id).as(OpenIDAccount.simple.singleOpt)
  }

  def findByEmail(email: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where email = {email}
      """
    ).on('email -> email).as(OpenIDAccount.simple.singleOpt)
  }

  def findByProfileId(id: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where profile_id = {id}
      """
    ).on('id -> id).as(OpenIDAccount.simple.singleOpt)
  }

  def create(email: String, profile_id: String): Option[OpenIDAccount] = DB.withConnection { implicit connection =>
    println("Inserting new user: " + email)
    SQL(
      """INSERT INTO users (id, email, profile_id) VALUES (DEFAULT,{email},{profile_id})"""
    ).on('email -> email, 'profile_id -> profile_id).executeUpdate

    // Nasty hack around DB types...
    if (connection.getMetaData.getURL.contains("mysql")) {
      SQL(
        """SELECT * FROM users WHERE id = LAST_INSERT_ID()"""
      ).as(OpenIDAccount.simple.singleOpt)
    } else {
      // Assuming POSTGRES
      SQL(
        """SELECT * FROM users WHERE id = currval('users_id_seq')"""
      ).as(OpenIDAccount.simple.singleOpt)
    }
  }

  def findByResetToken(token: String): Option[OpenIDAccount] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT u.*, t.token FROM users u, token t WHERE u.profile_id = t.profile_id AND t.token = {token}"""
    ).on('token -> token).as(OpenIDAccount.simple.singleOpt)
  }

  def expireToken(token: String): Unit = DB.withConnection { implicit connection =>
    SQL("""DELETE FROM token WHERE token = {token}""").on('token -> token).executeUpdate
  }

  def createResetToken(token: UUID, profileId: String): Unit = DB.withConnection { implicit connection =>
    SQL("""INSERT INTO token (profile_id, token) VAlUES ({profile_id}, {token})""")
      .on('profile_id -> profileId, 'token -> token.toString).executeInsert()
  }
}

// -- Associations

case class OpenIDAssociation(id: Long, userid: Long, url: String, user: Option[OpenIDAccount] = None) {

  lazy val users: Seq[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users
        join openid_association on openid_association.user_id = users.id
        where openid_association.id = {id}
      """
    ).on('id -> id).as(OpenIDAccount.simple *)
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
    simple ~ OpenIDAccount.simple map {
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

class OpenIDAccountDAOPlugin(app: play.api.Application) extends AccountDAO {
  def findByProfileId(profile_id: String) = OpenIDAccount.findByProfileId(profile_id)
  def findByEmail(email: String) = OpenIDAccount.findByEmail(email)
  def findByResetToken(token: String) = OpenIDAccount.findByResetToken(token)
  def createResetToken(token: UUID, profileId: String) = OpenIDAccount.createResetToken(token, profileId)
  def expireToken(token: String) = OpenIDAccount.expireToken(token)
  def create(email: String, profile_id: String) = OpenIDAccount.create(email, profile_id)
}

