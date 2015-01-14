
import java.io.File
import java.sql.CallableStatement
import models.AccountDAO
import models.sql.{SqlAccount, OAuth2Association, OpenIDAssociation}
import play.api.db.DB
import play.api.http.{ContentTypes, HeaderNames}
import play.api.test.FakeApplication

/**
 * User: mike
 */
package object helpers {

  val jsonPostHeaders: Map[String, String] = Map(
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )

  val formPostHeaders: Map[String,String] = Map(
    HeaderNames.CONTENT_TYPE -> ContentTypes.FORM
  )

  /**
   * Load database fixtures.
   */
  private[helpers] def loadSqlFixtures(implicit app: play.api.Application) = {
    val userDAO: AccountDAO = SqlAccount
    mocks.users.map { case (profile, account) =>
      val acc = userDAO.create(account.id, account.email, verified = account.verified, staff = account.staff,
        allowMessaging = account.allowMessaging)
      OpenIDAssociation.addAssociation(acc, acc.id + "-openid-test-url")
      OAuth2Association.addAssociation(acc, acc.id + "1234", "google")
    }
  }

  /**
   * Load a file containing SQL statements into the DB.
   */
  private[helpers] def loadSqlResource(resource: String)(implicit app: FakeApplication) = DB.withConnection { conn =>
    val file = new File(getClass.getClassLoader.getResource(resource).toURI)
    val path = file.getAbsolutePath
    val statement: CallableStatement = conn.prepareCall(s"RUNSCRIPT FROM '$path'")
    statement.execute()
    conn.commit()
  }
}
