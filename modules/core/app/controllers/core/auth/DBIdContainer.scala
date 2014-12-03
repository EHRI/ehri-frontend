package controllers.core.auth

import jp.t2v.lab.play2.auth.{AuthenticityToken, IdContainer}
import java.security.SecureRandom
import scala.util.Random
import scala.annotation.tailrec
import play.api.Play.current
import play.api.db.DB
import java.sql.Connection
import models.AccountDAO

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class DBIdContainer(userDAO: AccountDAO) extends IdContainer[String] {

  private val random = new Random(new SecureRandom())

  def startNewSession(userId: String, timeoutInSeconds: Int): AuthenticityToken = DB.withTransaction { implicit tx =>
    userDAO.removeLoginTokens(userId)
    val token = generate
    userDAO.storeLoginToken(token, userId, timeoutInSeconds)
    token
  }

  @tailrec
  private final def generate(implicit conn: Connection): AuthenticityToken = {
    val table = "abcdefghijklmnopqrstuvwxyz1234567890_.!~*'()"
    val token = Stream.continually(random.nextInt(table.size)).map(table).take(64).mkString
    if (userDAO.getByLoginToken(token).isDefined) generate else token
  }

  def remove(token: AuthenticityToken): Unit = userDAO.removeLoginToken(token)

  def get(token: AuthenticityToken): Option[String] = userDAO.getByLoginToken(token)

  def prolongTimeout(token: AuthenticityToken, timeoutInSeconds: Int): Unit = DB.withTransaction { implicit tx =>
    userDAO.getByLoginToken(token).foreach(userDAO.storeLoginToken(token, _, timeoutInSeconds))
  }
}
