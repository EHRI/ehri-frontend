package models.sql

import play.api.db.DB
import anorm._
import java.util.UUID
import play.api.Play.current

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait TokenManager {
  val profile_id: String


  def expireTokens(): Unit = DB.withConnection { implicit connection =>
    SQL("""DELETE FROM token WHERE profile_id = {profile_id}""")
      .on('profile_id -> profile_id).executeUpdate
  }

  def createResetToken(token: UUID): Unit = DB.withConnection { implicit connection =>
    SQL(
      """INSERT INTO token (profile_id, token, expires)
         VAlUES ({profile_id}, {token}, DATE_ADD(NOW(), INTERVAL 1 HOUR))""")
      .on('profile_id -> profile_id, 'token -> token.toString).executeInsert()
  }
}
