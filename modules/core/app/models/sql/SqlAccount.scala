package models.sql

import models.{HashedPassword, AccountDAO, Account}
import play.api.db.DB
import anorm._
import anorm.SqlParser._
import anorm.~
import play.api.Play.current
import java.util.UUID
import play.api.Logger

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlAccount(id: String, email: String, verified: Boolean = false, staff: Boolean = false, active: Boolean = true,
                       allowMessaging: Boolean = false) extends Account {

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

  def hasPassword: Boolean = DB.withConnection { implicit  connection =>
    try {
    SQL("select count(data) from user_auth WHERE id = {id}")
      .on('id -> id).as(scalar[Long].single) > 0
    } catch {
      case c: Throwable => Logger.error("Error checking password: {}", c); throw c
    }
  }

  def setPassword(data: HashedPassword): Account = DB.withTransaction { implicit connection =>
    if (hasPassword) updatePassword(data) else try {
      SQL("INSERT INTO user_auth (id, data) VALUES ({id},{data})")
        .on('id -> id, 'data -> data.toString).executeInsert()
      this
    } catch {
      case c: Throwable => Logger.error("Error setting password: {}", c); throw c
    }
  }

  def updatePassword(data: HashedPassword): Account = DB.withConnection{ implicit connection =>
    try {
      SQL("UPDATE user_auth SET data = {data} WHERE id = {id}")
        .on('id -> id, 'data -> data.toString).executeUpdate()
      this
    } catch {
      case c: Throwable => Logger.error("Error updating password: {}", c); throw c
    }
  }

  def setVerified(): Account = DB.withTransaction { implicit connection =>
    if (!verified) {
      SQL("UPDATE users SET verified = 1 WHERE id = {id}")
        .on('id -> id, 'verified -> true).executeUpdate()
      SQL("""DELETE FROM token WHERE id = {id}""").on('id -> id).execute()
    }
    this.copy(verified = true)
  }

  def verify(token: String): Account = DB.withTransaction { implicit connection =>
    SQL("UPDATE users SET verified = {verified} WHERE id = {id}")
      .on('id -> id, 'verified -> true).executeUpdate()
    SQL("""DELETE FROM token WHERE token = {token}""").on('token -> token).execute()
    this.copy(verified = true)
  }

  def setActive(active: Boolean): Account = DB.withTransaction { implicit connection =>
    SQL("UPDATE users SET active = {active} WHERE id = {id}")
      .on('id -> id, 'active -> active).executeUpdate()
    this.copy(active = active)
  }

  def setStaff(staff: Boolean): Account = DB.withTransaction { implicit connection =>
    SQL("UPDATE users SET staff = {staff} WHERE id = {id}")
      .on('id -> id, 'staff -> staff).executeUpdate()
    this.copy(staff = staff)
  }

  def setAllowMessaging(allowMessaging: Boolean): Account = DB.withTransaction { implicit connection =>
    SQL("UPDATE users SET allow_messaging = {allowMessaging} WHERE id = {id}")
      .on('id -> id, 'allowMessaging -> allowMessaging).executeUpdate()
    this.copy(allowMessaging = allowMessaging)
  }

  def expireTokens(): Unit = DB.withConnection { implicit connection =>
    SQL("""DELETE FROM token WHERE id = {id}""").on('id -> id).executeUpdate
  }

  def createResetToken(token: UUID): Unit = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO token (id, token, expires, is_sign_up)
         VAlUES ({id}, {token}, DATE_ADD(NOW(), INTERVAL 1 HOUR), 0)""")
      .on('id -> id, 'token -> token.toString).executeInsert()
  }

  def createValidationToken(token: UUID): Unit = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO token (id, token, expires, is_sign_up)
         VAlUES ({id}, {token}, DATE_ADD(NOW(), INTERVAL 1 DAY), 1)""")
      .on('id -> id, 'token -> token.toString).executeInsert()
  }

  def update(): Unit = DB.withConnection { implicit connection =>
    SQL(
      """
        UPDATE users
        SET active = {active}, staff = {staff}, verified = {verifed}, email = {email},
            allow_messaging = {allowMessaging}
        WHERE id = {id}
      """.stripMargin).on(
      'id -> id, 'active -> active, 'verified -> verified, 'email -> email, 'staff -> staff,
        'allowMessaging -> allowMessaging
    ).executeUpdate()
  }
}

object SqlAccount extends AccountDAO {

  val simple = {
    get[String]("users.id") ~
      get[String]("users.email") ~
      get[Boolean]("users.verified") ~
      get[Boolean]("users.staff") ~
      get[Boolean]("users.active") ~
      get[Boolean]("users.allow_messaging") map {
      case id ~ email ~ verified ~ staff ~ active ~ allowMessaging =>
        SqlAccount(id, email, verified, staff, active, allowMessaging)
    }
  }

  def findAll: Seq[SqlAccount] = DB.withConnection { implicit connection =>
    SQL("select * from users").as(SqlAccount.simple *)
  }

  def findVerifiedByEmail(email: String, verified: Boolean = true): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users where email = {email} and verified = {verified}
      """
    ).on('email -> email, 'verified -> verified).as(SqlAccount.simple.singleOpt)
  }

  def findByEmail(email: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """select * from users where email = {email}"""
    ).on('email -> email).as(SqlAccount.simple.singleOpt)
  }

  def findVerifiedByProfileId(id: String, verified: Boolean = true): Option[Account] = DB.withConnection { implicit connection =>
    SQL("select * from users where id = {id} and verified = {verified}")
      .on('id -> id, 'verified -> verified).as(SqlAccount.simple.singleOpt)
  }

  def findByProfileId(id: String): Option[Account] = DB.withConnection { implicit connection =>
    SQL("select * from users where id = {id}").on('id -> id)
      .as(SqlAccount.simple.singleOpt)
  }

  def create(id: String, email: String, verified: Boolean, staff: Boolean, allowMessaging: Boolean = true): SqlAccount =
      DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO users (id, email, verified, staff, allow_messaging)
        VALUES ({id}, {email}, {verified}, {staff}, {allowMessaging})"""
    ).on('id -> id, 'email -> email, 'verified -> verified, 'staff -> staff, 'allowMessaging -> allowMessaging)
      .executeUpdate
    SqlAccount(id, email, verified, staff)
  }

  def createWithPassword(id: String, email: String, verified: Boolean, staff: Boolean, allowMessaging: Boolean = true,
                         hashed: HashedPassword): SqlAccount
      = DB.withTransaction { implicit connection =>
    SQL(
      """INSERT INTO users (id, email, verified, staff, allow_messaging)
        VALUES ({id}, {email}, {verified}, {staff}, {allowMessaging})"""
    ).on('id -> id, 'email -> email, 'verified -> verified, 'staff -> staff, 'allowMessaging -> allowMessaging).executeUpdate
    SQL("INSERT INTO user_auth (id, data) VALUES ({id},{data})")
      .on('id -> id, 'data -> hashed.toString).executeInsert()
    SqlAccount(id, email, verified, staff)
  }

  def findByResetToken(token: String, isSignUp: Boolean = false): Option[Account] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT u.*, t.token FROM users u, token t
         WHERE u.id = t.id AND t.token = {token}
          AND is_sign_up = {is_sign_up}
          AND t.expires > NOW()"""
    ).on('token -> token, 'is_sign_up -> isSignUp).as(SqlAccount.simple.singleOpt)
  }
}
