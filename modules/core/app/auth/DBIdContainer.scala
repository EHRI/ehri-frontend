package auth

//import java.security.SecureRandom
//import java.sql.Connection
//
//import jp.t2v.lab.play2.auth.{AuthenticityToken, IdContainer}
//import models.AccountDAO
//import play.api.Play.current
//import play.api.db.DB
//
//import scala.annotation.tailrec
//import scala.util.Random
//
///**
// * @author Mike Bryant (http://github.com/mikesname)
// */
//class DBIdContainer(accounts: AccountDAO) extends IdContainer[String] {
//
//  private val random = new Random(new SecureRandom())
//
//  def startNewSession(userId: String, timeoutInSeconds: Int): AuthenticityToken = DB.withTransaction { implicit tx =>
//    accounts.removeLoginTokens(userId)
//    val token = generate
//    accounts.storeLoginToken(token, userId, timeoutInSeconds)
//    token
//  }
//
//  @tailrec
//  private final def generate(implicit conn: Connection): AuthenticityToken = {
//    val table = "abcdefghijklmnopqrstuvwxyz1234567890_.!~*'()"
//    val token = Stream.continually(random.nextInt(table.size)).map(table).take(64).mkString
//    if (accounts.getByLoginToken(token).isDefined) generate else token
//  }
//
//  def remove(token: AuthenticityToken): Unit = accounts.removeLoginToken(token)
//
//  def get(token: AuthenticityToken): Option[String] = accounts.getByLoginToken(token)
//
//  def prolongTimeout(token: AuthenticityToken, timeoutInSeconds: Int): Unit = DB.withTransaction { implicit tx =>
//    accounts.getByLoginToken(token).foreach(accounts.storeLoginToken(token, _, timeoutInSeconds))
//  }
//}
