package auth.sql

import java.sql.Connection
import scala.language.postfixOps
import scala.languageFeature.postfixOps
import java.util.UUID

import auth.{HashedPassword, OpenIdAssociationManager, OAuth2AssociationManager, AccountManager}
import models.{OAuth2Association, OpenIDAssociation, Account}
import play.api.db.DB
import play.api.libs.concurrent.Akka
import utils.PageParams
import anorm.SqlParser._
import anorm._
import scala.concurrent.{Future, ExecutionContext}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlAccountManager()(implicit app: play.api.Application) extends AccountManager {

  import SqlAccountManager._

  override protected implicit def executionContext: ExecutionContext =
    Akka.system.dispatchers.lookup("contexts.simple-db-looksups")

  def oAuth2: OAuth2AssociationManager = new SqlOAuth2AssociationManager()

  def openId: OpenIdAssociationManager = new SqlOpenIdAssociationManager()

  def setLoggedIn(account: Account): Future[Account] = Future {
    DB.withTransaction { implicit conn =>
      SQL("UPDATE users SET last_login = NOW() WHERE id = {id}")
        .on('id -> account.id).executeUpdate()
      account
    }
  }(executionContext)

  def authenticate(email: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    DB.withTransaction { implicit conn =>
      for {
        account <- getByEmail(email)
        hashedPw <- getPassword(account.id) if hashedPw.check(pw) && (if (verifiedOnly) account.verified else true)
      } yield {
        account
      }
    }
  }(executionContext)

  def verify(account: Account, token: String): Future[Option[Account]] = Future {
    DB.withTransaction { implicit conn =>
      SQL("UPDATE users SET verified = {verified} WHERE id = {id}")
        .on('id -> account.id, 'verified -> true).executeUpdate()
      SQL("""DELETE FROM token WHERE token = {token}""").on('token -> token).execute()
      Some(account)
    }
  }(executionContext)

  def findById(id: String): Future[Option[Account]] = Future {
    DB.withConnection { implicit conn =>
      getById(id)
    }
  }(executionContext)

  def findAllById(ids: Seq[String]): Future[Seq[Account]] = Future {
    DB.withConnection { implicit conn =>
      SQL("SELECT * FROM users WHERE id IN ({ids})")
      .on('ids -> ids).as(userParser *)
    }
  }(executionContext)

  def findByResetToken(token: String, isSignUp: Boolean): Future[Option[Account]] = Future {
    DB.withConnection { implicit conn =>
      SQL(
        """SELECT u.*, t.token FROM users u, token t
         WHERE u.id = t.id AND t.token = {token}
          AND is_sign_up = {is_sign_up}
          AND t.expires > NOW()"""
      ).on('token -> token, 'is_sign_up -> isSignUp).as(userParser.singleOpt)
    }
  }(executionContext)

  def update(account: Account): Future[Account] = Future {
    DB.withConnection { implicit connection =>
      SQL(
        """UPDATE users
          |SET
          | email = {email},
          | verified = {verified},
          | staff = {staff},
          | active = {active},
          | allow_messaging = {allowMessaging}
          |WHERE id = {id}""".stripMargin)
        .on(
          'id -> account.id,
          'email -> account.email,
          'verified -> account.verified,
          'staff -> account.staff,
          'active -> account.active,
          'allowMessaging -> account.allowMessaging
        ).executeUpdate()
      account
    }
  }(executionContext)

  def delete(id: String): Future[Boolean] = Future {
    DB.withConnection { implicit connection =>
      val rows: Int = SQL(
        """DELETE FROM users WHERE id = {id} LIMIT 1""").on('id -> id).executeUpdate()
      rows > 0
    }
  }(executionContext)

  def expireTokens(id: String): Future[Unit] = Future {
    DB.withConnection { implicit conn =>
      SQL("""DELETE FROM token WHERE id = {id}""").on('id -> id).executeUpdate()
    }
    ()
  }(executionContext)

  def findAll(params: PageParams): Future[Seq[Account]] = Future {
    DB.withConnection { implicit conn =>
      SQL("SELECT * FROM users").as(userParser *)
    }
  }(executionContext)

  def findByEmail(email: String): Future[Option[Account]] = Future {
    DB.withConnection { implicit  conn =>
      getByEmail(email)
    }
  }(executionContext)

  def createValidationToken(id: String, uuid: UUID): Future[Unit] = Future {
    DB.withConnection { implicit conn =>
      SQL(
        """INSERT INTO token (id, token, expires, is_sign_up)
         VAlUES ({id}, {token}, DATE_ADD(NOW(), INTERVAL 1 WEEK), 1)""")
        .on('id -> id, 'token -> uuid.toString).executeInsert()
    }
    ()
  }(executionContext)

  def createResetToken(id: String, uuid: UUID): Future[Unit] = Future {
    DB.withConnection { implicit conn =>
      SQL(
        """INSERT INTO token (id, token, expires, is_sign_up)
         VAlUES ({id}, {token}, DATE_ADD(NOW(), INTERVAL 1 HOUR), 0)""")
        .on('id -> id, 'token -> uuid.toString).executeInsert()
    }
    ()
  }(executionContext)

  def create(account: Account): Future[Account] = Future {
    DB.withConnection { implicit conn =>
      SQL(
        """INSERT INTO users (id, email, verified, staff, allow_messaging)
        VALUES ({id}, {email}, {verified}, {staff}, {allowMessaging})"""
      ).on('id -> account.id,
          'email -> account.email,
          'verified -> account.verified,
          'staff -> account.staff,
          'allowMessaging -> account.allowMessaging)
        .executeUpdate
      account
    }
  }(executionContext)

  def setPassword(id: String, hashed: HashedPassword): Future[Unit] = Future {
    // It's a pain but there's no portal upsert solution which works across
    // DBs...
    DB.withConnection { implicit transaction =>
      val rows: Int = SQL("""UPDATE user_auth SET data = {data} WHERE id = {id}""")
        .on('id -> id, 'data -> hashed.s).executeUpdate()
      if (rows == 0) {
        SQL(
          """INSERT INTO user_auth (id, data) VALUES ({id}, {data})""".stripMargin)
          .on('id -> id, 'data -> hashed.s).executeInsert()
      }
      ()
    }
  }(executionContext)

  private def getPassword(id: String)(implicit conn: Connection): Option[HashedPassword] = {
    SQL("SELECT data FROM user_auth WHERE id = {id}").on('id -> id)
      .as(str("data").singleOpt).map(HashedPassword.fromHashed)
  }

  private def getByEmail(email: String)(implicit conn: Connection): Option[Account] = {
    SQL(
      """SELECT * FROM users WHERE email = {email}"""
    ).on('email -> email).as(userParser.singleOpt)
  }

  private def getById(id: String)(implicit conn: Connection): Option[Account] = {
    SQL(
      """SELECT * FROM users WHERE id = {id}"""
    ).on('id -> id).as(userParser.singleOpt)
  }
}

object SqlAccountManager {
  val userParser = {
    get[String]("users.id") ~
      get[String]("users.email") ~
      get[Boolean]("users.verified") ~
      get[Boolean]("users.staff") ~
      get[Boolean]("users.active") ~
      get[Boolean]("users.allow_messaging") map {
      case id ~ email ~ verified ~ staff ~ active ~ allowMessaging =>
        Account(id, email, verified, staff, active, allowMessaging)
    }
  }
}