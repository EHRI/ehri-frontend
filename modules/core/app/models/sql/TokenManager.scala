package models.sql

import play.api.db.DB
import anorm._
import java.util.UUID
import play.api.Play.current

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait TokenManager {
  val id: String

  def expireTokens(): Unit = DB.withConnection { implicit connection =>
    SQL("""DELETE FROM token WHERE id = {id}""").on('id -> id).executeUpdate
  }

  def createResetToken(token: UUID): Unit = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO token (id, token, expires)
         VAlUES ({id}, {token}, DATE_ADD(NOW(), INTERVAL 1 HOUR))""")
      .on('id -> id, 'token -> token.toString).executeInsert()
  }
}
