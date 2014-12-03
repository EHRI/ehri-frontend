package controllers.core.auth

import jp.t2v.lab.play2.auth.{AuthenticityToken, IdContainer}
import java.security.SecureRandom
import scala.util.Random
import scala.annotation.tailrec
import play.api.Play.current
import play.api.db.DB
import anorm._
import anorm.SqlParser._
import java.sql.Connection

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class DBIdContainer extends IdContainer[String] {

  private val random = new Random(new SecureRandom())

  def startNewSession(userId: String, timeoutInSeconds: Int): AuthenticityToken = DB.withTransaction { implicit tx =>
    removeByUserId(userId)
    val token = generate
    store(token, userId, timeoutInSeconds)
    token
  }

  @tailrec
  private final def generate(implicit conn: Connection): AuthenticityToken = {
    val table = "abcdefghijklmnopqrstuvwxyz1234567890_.!~*'()"
    val token = Stream.continually(random.nextInt(table.size)).map(table).take(64).mkString
    if (lookup(token).isDefined) generate else token
  }

  private def lookup(token: AuthenticityToken)(implicit conn: Connection): Option[String] = {
    SQL"SELECT id FROM user_auth_token WHERE token = $token".as(str("id").singleOpt)
  }

  private def removeByUserId(userId: String)(implicit conn: Connection) {
    SQL"DELETE FROM user_auth_token WHERE id = $userId".executeUpdate()
  }

  private def store(token: AuthenticityToken, userId: String, timeoutInSeconds: Int)(implicit conn: Connection) {
    SQL"""
      INSERT INTO user_auth_token (id, token, expires)
      VALUES ($userId, $token, TIMESTAMPADD(SECOND, $timeoutInSeconds, NOW()))
    """.executeInsert()
  }

  private def update(token: AuthenticityToken, userId: String, timeoutInSeconds: Int)(implicit conn: Connection) {
    SQL"""
      UPDATE user_auth_token SET expires = TIMESTAMPADD(SECOND, $timeoutInSeconds, NOW())
      WHERE token = $token""".executeUpdate()
  }

  def remove(token: AuthenticityToken): Unit = DB.withConnection { implicit conn =>
    SQL"DELETE FROM user_auth_token WHERE token = $token".executeUpdate()
  }

  def get(token: AuthenticityToken): Option[String] = DB.withConnection { implicit conn =>
    lookup(token)
  }

  def prolongTimeout(token: AuthenticityToken, timeoutInSeconds: Int): Unit = DB.withTransaction { implicit tx =>
    lookup(token).foreach(update(token, _, timeoutInSeconds))
  }
}
