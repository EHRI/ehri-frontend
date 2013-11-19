package models.sql

import models.{HashedPassword, AccountDAO, Account}
import play.api.db.DB
import anorm._
import anorm.SqlParser._
import anorm.~
import play.api.Play.current
import java.util.UUID

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlAccount(id: String, email: String, staff: Boolean = false) extends Account {

  def delete(): Boolean = DB.withConnection { implicit connection =>
    val res: Int = SQL(
      """DELETE FROM users WHERE id = {id}""").on('id -> id).executeUpdate()
    res == 1
  }

  override def password: Option[HashedPassword] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT data FROM user_auth WHERE user_auth.id = {id} LIMIT 1"""
    ).on('id -> id).as(str("data").singleOpt).map(HashedPassword.fromHashed)
  }

  def setPassword(data: HashedPassword): Account = DB.withConnection{ implicit connection =>
    val res = SQL("INSERT INTO user_auth (id, data) VALUES ({id},{data})")
      .on('id -> id, 'data -> data.toString).executeInsert()
    this
  }

  def updatePassword(data: HashedPassword): Account = DB.withConnection{ implicit connection =>
    val res = SQL("UPDATE user_auth SET data={data} WHERE id={id}")
      .on('id -> id, 'data -> data.toString).executeUpdate()
    this
  }

  def expireTokens(): Unit = DB.withConnection { implicit connection =>
    SQL("""DELETE FROM token WHERE id = {id}""").on('id -> id).executeUpdate
  }

  def createResetToken(token: UUID): Unit = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO token (id, token, expires)
         VAlUES ({id}, {token}, DATE_ADD(NOW(), INTERVAL 1 HOUR))""")
      .on('id -> id, 'token -> token.toString).executeInsert()
  }

  def isStaff = false // STUB

}

object SqlAccount extends AccountDAO {

  val simple = {
    get[String]("users.id") ~
      get[String]("users.email") ~
      get[Boolean]("users.staff") map {
      case id ~ email ~ staff => SqlAccount(id, email, staff)
    }
  }

  def findAll: Seq[SqlAccount] = DB.withConnection { implicit connection =>
    SQL("select * from users").as(SqlAccount.simple *)
  }

  def findByEmail(email: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where email = {email}
      """
    ).on('email -> email).as(SqlAccount.simple.singleOpt)
  }

  def findByProfileId(id: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL("select * from users where id = {id}")
      .on('id -> id).as(SqlAccount.simple.singleOpt)
  }

  def create(id: String, email: String, staff: Boolean = false): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (id, email, staff) VALUES ({id}, {email}, {staff})"""
    ).on('id -> id, 'email -> email, 'staff -> staff).executeUpdate
    findByProfileId(id)
  }

  def createWithPassword(id: String, email: String, staff: Boolean = false, hashed: HashedPassword): Option[Account]
    = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (id, email, staff) VALUES ({id}, {email}, {staff})"""
    ).on('id -> id, 'email -> email, 'staff -> staff).executeUpdate
    SQL("INSERT INTO user_auth (id, data) VALUES ({id},{data})")
      .on('id -> id, 'data -> hashed.toString).executeInsert()
    findByProfileId(id)
  }

  def findByResetToken(token: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT u.*, t.token FROM users u, token t
         WHERE u.id = t.id AND t.token = {token}
          AND t.expires > NOW()"""
    ).on('token -> token).as(SqlAccount.simple.singleOpt)
  }
}

class SqlAccountDAOPlugin(app: play.api.Application) extends AccountDAO {
  def findByProfileId(id: String) = SqlAccount.findByProfileId(id)
  def findByEmail(email: String) = SqlAccount.findByEmail(email)
  def findByResetToken(token: String) = SqlAccount.findByResetToken(token)
  def create(id: String, email: String, staff: Boolean = false) = SqlAccount.create(id, email, staff)
  def createWithPassword(id: String, email: String, staff: Boolean = false, hashed: HashedPassword)
      = SqlAccount.createWithPassword(id, email, staff, hashed)
}

