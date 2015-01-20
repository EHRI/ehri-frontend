
import java.io.File
import java.sql.CallableStatement
import auth.AccountManager
import auth.sql.SqlAccountManager
import play.api.db.DB
import play.api.test.FakeApplication

import scala.concurrent.Await
import scala.concurrent.duration._


/**
 * User: mike
 */
package object helpers {

  import scala.concurrent.ExecutionContext.Implicits.global
  /**
   * Load database fixtures.
   */
  def loadSqlFixtures(implicit app: play.api.Application) = {
    val userDAO: AccountManager = SqlAccountManager()
    mocks.users.map { case (profile, account) =>
      val acc = Await.result(userDAO.create(account), 20.seconds)
      Await.result(userDAO.openid.addAssociation(acc, acc.id + "-openid-test-url"), 20.seconds)
      Await.result(userDAO.oauth2.addAssociation(acc, acc.id + "1234", "google"), 20.seconds)
    }
  }

  /**
   * Load a file containing SQL statements into the DB.
   */
  def loadSqlResource(resource: String)(implicit app: FakeApplication) = DB.withConnection { conn =>
    val file = new File(getClass.getClassLoader.getResource(resource).toURI)
    val path = file.getAbsolutePath
    val statement: CallableStatement = conn.prepareCall(s"RUNSCRIPT FROM '$path'")
    statement.execute()
    conn.commit()
  }
}
