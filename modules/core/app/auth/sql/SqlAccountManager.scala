package auth.sql

import java.sql.Connection
import org.joda.time.DateTime

import scala.language.postfixOps
import scala.languageFeature.postfixOps
import java.util.UUID

import auth.{HashedPassword, OpenIdAssociationManager, OAuth2AssociationManager, AccountManager}
import models.Account
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

  override def oAuth2: OAuth2AssociationManager = new SqlOAuth2AssociationManager()

  override def openId: OpenIdAssociationManager = new SqlOpenIdAssociationManager()

  override def setLoggedIn(account: Account): Future[Account] = Future {
    DB.withTransaction { implicit conn =>
      SQL"UPDATE users SET last_login = NOW() WHERE id = ${account.id}".executeUpdate()
      // This is a bit dodgy
      account.copy(lastLogin = Some(DateTime.now()))
    }
  }(executionContext)

  override def authenticateById(id: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    DB.withTransaction { implicit conn =>
      for {
        account <- getById(id)
        hashedPw <- account.password if hashedPw.check(pw) && (if (verifiedOnly) account.verified else true)
      } yield {
        account
      }
    }
  }(executionContext)

  override def authenticateByEmail(email: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    DB.withTransaction { implicit conn =>
      for {
        account <- getByEmail(email)
        hashedPw <- account.password if hashedPw.check(pw) && (if (verifiedOnly) account.verified else true)
      } yield {
        account
      }
    }
  }(executionContext)


  override def get(id: String): Future[Account] = Future {
    DB.withConnection { implicit conn =>
      getById(id).map { account =>
        account
      }.getOrElse(throw new NoSuchElementException(id))
    }
  }(executionContext)

  override def verify(account: Account, token: String): Future[Option[Account]] = Future {
    DB.withTransaction { implicit conn =>
      SQL"UPDATE users SET verified = true WHERE id = ${account.id}".executeUpdate()
      SQL"DELETE FROM token WHERE token = $token".execute()
      Some(account)
    }
  }(executionContext)

  override def findById(id: String): Future[Option[Account]] = Future {
    DB.withConnection { implicit conn =>
      getById(id)
    }
  }(executionContext)

  override def findAllById(ids: Seq[String]): Future[Seq[Account]] = Future {
    DB.withConnection { implicit conn =>
      SQL"SELECT * FROM users WHERE users.id IN ($ids)".as(userParser *)
    }
  }(executionContext)

  override def findByToken(token: String, isSignUp: Boolean): Future[Option[Account]] = Future {
    DB.withConnection { implicit conn =>
      SQL"""
        SELECT u.*, t.token FROM users u, token t
          WHERE u.id = t.id AND t.token = $token
            AND is_sign_up = $isSignUp
            AND t.expires > NOW()
      """.as(userParser.singleOpt)
    }
  }(executionContext)

  override def update(account: Account): Future[Account] = Future {
    DB.withConnection { implicit connection =>
      SQL"""
        UPDATE users
        SET
          email = ${account.email},
          verified = ${account.verified},
          staff = ${account.staff},
          active = ${account.active},
          allow_messaging = ${account.allowMessaging},
          password = ${account.password}
        WHERE id = ${account.id}
        """.executeUpdate()
      account
    }
  }(executionContext)

  override def delete(id: String): Future[Boolean] = Future {
    DB.withConnection { implicit connection =>
      val rows: Int = SQL"""DELETE FROM users WHERE id = $id LIMIT 1""".executeUpdate()
      rows > 0
    }
  }(executionContext)

  override def expireTokens(id: String): Future[Unit] = Future {
    DB.withConnection { implicit conn =>
      SQL"""DELETE FROM token WHERE id = $id""".executeUpdate()
    }
    ()
  }(executionContext)

  override def findAll(params: PageParams): Future[Seq[Account]] = Future {
    DB.withConnection { implicit conn =>
      val limit = if (params.hasLimit) params.limit else Integer.MAX_VALUE
      SQL"SELECT * FROM users ORDER BY id LIMIT $limit OFFSET ${params.offset}"
        .as(userParser *)
    }
  }(executionContext)

  override def findByEmail(email: String): Future[Option[Account]] = Future {
    DB.withConnection { implicit  conn =>
      getByEmail(email)
    }
  }(executionContext)

  override def createToken(id: String, uuid: UUID, isSignUp: Boolean): Future[Unit] = Future {
    DB.withConnection { implicit conn =>
      // Bit gross this, not sure how to change expiry conditionally...
      (if (isSignUp)
        SQL"""INSERT INTO token (id, token, expires, is_sign_up)
           VAlUES ($id, $uuid, TIMESTAMPADD(WEEK, 1, NOW()), 1)"""
      else
        SQL"""INSERT INTO token (id, token, expires, is_sign_up)
           VAlUES ($id, $uuid, TIMESTAMPADD(HOUR, 1, NOW()), 0)""").executeInsert()
    }
    ()
  }(executionContext)

  override def create(account: Account): Future[Account] = Future {
    DB.withConnection { implicit conn =>
      SQL"""INSERT INTO users
        (id, email, verified, staff, allow_messaging, password)
        VALUES (
          ${account.id},
          ${account.email},
          ${account.verified},
          ${account.staff},
          ${account.allowMessaging},
          ${account.password}
      )""".executeInsert()
      account
    }
  }(executionContext)

  private def getByEmail(email: String)(implicit conn: Connection): Option[Account] =
    SQL"SELECT * FROM users WHERE users.email = $email".as(userParser.singleOpt)

  private def getById(id: String)(implicit conn: Connection): Option[Account] =
    SQL"SELECT * FROM users WHERE users.id = $id".as(userParser.singleOpt)
}

object SqlAccountManager {
  /**
   * Implicit conversion from HashedPassword to Anorm statement value
   */
  implicit def pwToStatement: ToStatement[HashedPassword] = new ToStatement[HashedPassword] {
    def set(s: java.sql.PreparedStatement, index: Int, aValue: HashedPassword): Unit =
      s.setString(index, aValue.s)
  }

  /**
   * Implicit conversion from Anorm row to HashedPassword
   */
  implicit def rowToPw: Column[HashedPassword] = {
    Column.nonNull[HashedPassword] { (value, meta) =>
      value match {
        case v: String => Right(HashedPassword.fromHashed(v))
        case _ => Left(TypeDoesNotMatch(
          s"Cannot convert $value:${value.asInstanceOf[AnyRef].getClass} to hashed password for column ${meta.column}"))
      }
    }
  }

  val userParser = {
    str("users.id") ~
      str("users.email") ~
      bool("users.verified") ~
      bool("users.staff") ~
      bool("users.active") ~
      bool("users.allow_messaging") ~
      get[Option[DateTime]]("users.last_login") ~
      get[Option[HashedPassword]]("users.password") map {
      case id ~ email ~ verified ~ staff ~ active ~ allowMessaging ~ login ~ pw =>
        Account(id, email, verified, staff, active, allowMessaging, login, pw)
    }
  }
}