package auth.sql

import java.sql.Connection
import org.joda.time.DateTime

import scala.language.postfixOps
import scala.languageFeature.postfixOps
import java.util.UUID

import auth.{HashedPassword, OpenIdAssociationManager, OAuth2AssociationManager, AccountManager}
import models.Account
import play.api.db.Database
import play.api.libs.concurrent.Akka
import utils.PageParams
import anorm.SqlParser._
import anorm._
import anorm.JodaParameterMetaData._
import scala.concurrent.{Future, ExecutionContext}

import javax.inject._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class SqlAccountManager @Inject()(implicit db: Database, app: play.api.Application) extends AccountManager {

  import SqlAccountManager._

  override protected implicit def executionContext: ExecutionContext =
    Akka.system.dispatchers.lookup("contexts.simple-db-lookups")

  override def oAuth2: OAuth2AssociationManager = new SqlOAuth2AssociationManager()

  override def openId: OpenIdAssociationManager = new SqlOpenIdAssociationManager()

  override def setLoggedIn(account: Account): Future[Account] = Future {
    db.withTransaction { implicit conn =>
      SQL"UPDATE users SET last_login = NOW() WHERE id = ${account.id}".executeUpdate()
      // This is a bit dodgy
      account.copy(lastLogin = Some(DateTime.now()))
    }
  }(executionContext)

  private def passwordMatches(account: Account, pw: String): Boolean =
    account.password.exists(hpw => if (!account.isLegacy) hpw.check(pw) else  hpw.checkLegacy(pw))

  override def authenticateById(id: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    db.withTransaction { implicit conn =>
      getById(id).filter { account =>
        passwordMatches(account, pw)
      }.filter { account =>
        if (verifiedOnly) account.verified else true
      }
    }
  }(executionContext)

  override def authenticateByEmail(email: String, pw: String, verifiedOnly: Boolean): Future[Option[Account]] = Future {
    db.withTransaction { implicit conn =>
      getByEmail(email).filter { account =>
        passwordMatches(account, pw)
      }.filter { account =>
        if (verifiedOnly) account.verified else true
      }
    }
  }(executionContext)


  override def get(id: String): Future[Account] = Future {
    db.withConnection { implicit conn =>
      getById(id).map { account =>
        account
      }.getOrElse(throw new NoSuchElementException(id))
    }
  }(executionContext)

  override def verify(account: Account, token: String): Future[Option[Account]] = Future {
    db.withTransaction { implicit conn =>
      SQL"UPDATE users SET verified = TRUE WHERE id = ${account.id}".executeUpdate()
      SQL"DELETE FROM token WHERE token = $token".execute()
      Some(account.copy(verified = true))
    }
  }(executionContext)

  override def findById(id: String): Future[Option[Account]] = Future {
    db.withConnection { implicit conn =>
      getById(id)
    }
  }(executionContext)

  override def findAllById(ids: Seq[String]): Future[Seq[Account]] =
    if (ids.isEmpty) Future.successful(Seq.empty)
    else Future {
      db.withConnection { implicit conn =>
        SQL"SELECT * FROM users WHERE users.id IN ($ids)".as(userParser *)
      }
    }(executionContext)

  override def findByToken(token: String, isSignUp: Boolean): Future[Option[Account]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        SELECT u.*, t.token FROM users u, token t
          WHERE u.id = t.id AND t.token = $token
            AND is_sign_up = $isSignUp
            AND t.expires > NOW()
      """.as(userParser.singleOpt)
    }
  }(executionContext)

  override def create(account: Account): Future[Account] = Future {
    db.withConnection { implicit conn =>
      SQL"""INSERT INTO users
        (id, email, verified, staff, allow_messaging, password, is_legacy)
        VALUES (
          ${account.id},
          ${account.email},
          ${account.verified},
          ${account.staff},
          ${account.allowMessaging},
          ${account.password},
          ${account.isLegacy}
      )""".executeInsert(SqlParser.scalar[String].singleOpt)
      getById(account.id).get
    }
  }(executionContext)

  override def update(account: Account): Future[Account] = Future {
    db.withConnection { implicit connection =>
      SQL"""
        UPDATE users
        SET
          email = ${account.email},
          staff = ${account.staff},
          active = ${account.active},
          allow_messaging = ${account.allowMessaging},
          password = ${account.password},
          is_legacy = ${account.isLegacy}
        WHERE id = ${account.id}
        """.executeUpdate()
      getById(account.id).get
    }
  }(executionContext)

  override def delete(id: String): Future[Boolean] = Future {
    db.withConnection { implicit connection =>
      val rows: Int = SQL"""DELETE FROM users WHERE id = $id""".executeUpdate()
      rows > 0
    }
  }(executionContext)

  override def expireTokens(id: String): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL"""DELETE FROM token WHERE id = $id""".executeUpdate()
    }
    ()
  }(executionContext)

  override def findAll(params: PageParams): Future[Seq[Account]] = Future {
    db.withConnection { implicit conn =>
      val limit = if (params.hasLimit) params.limit else Integer.MAX_VALUE
      SQL"SELECT * FROM users ORDER BY id LIMIT $limit OFFSET ${params.offset}"
        .as(userParser *)
    }
  }(executionContext)

  override def findByEmail(email: String): Future[Option[Account]] = Future {
    db.withConnection { implicit  conn =>
      getByEmail(email)
    }
  }(executionContext)

  override def createToken(id: String, uuid: UUID, isSignUp: Boolean): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      if (isSignUp) createSignupToken(id, uuid) else createResetToken(id, uuid)
    }
    ()
  }(executionContext)


  private def getByEmail(email: String)(implicit conn: Connection): Option[Account] =
    SQL"SELECT * FROM users WHERE users.email = $email".as(userParser.singleOpt)

  private def getById(id: String)(implicit conn: Connection): Option[Account] =
    SQL"SELECT * FROM users WHERE users.id = $id".as(userParser.singleOpt)

  private def createSignupToken(id: String, uuid: UUID)(implicit conn: Connection): Unit =
    // NB: calculating expires here to avoid DB compatibility issues...
    SQL"""INSERT INTO token (id, token, expires, is_sign_up)
           VALUES ($id, $uuid, ${DateTime.now().plusWeeks(1)}, TRUE)"""
      .executeInsert(SqlParser.scalar[String].singleOpt)

  private def createResetToken(id: String, uuid: UUID)(implicit conn: Connection): Unit =
    SQL"""INSERT INTO token (id, token, expires, is_sign_up)
           VALUES ($id, $uuid, ${DateTime.now().plusHours(1)}, FALSE)"""
      .executeInsert(SqlParser.scalar[String].singleOpt)
}

object SqlAccountManager {
  val userParser = {
    str("users.id") ~
      str("users.email") ~
      bool("users.verified") ~
      bool("users.staff") ~
      bool("users.active") ~
      bool("users.allow_messaging") ~
      get[Option[DateTime]]("users.created") ~
      get[Option[DateTime]]("users.last_login") ~
      get[Option[HashedPassword]]("users.password") ~
      get[Boolean]("users.is_legacy") map {
      case id ~ email ~ verified ~ staff ~ active ~ allowMessaging ~ created ~ login ~ pw ~ legacy =>
        Account(id, email, verified, staff, active, allowMessaging, created, login, pw, legacy)
    }
  }
}