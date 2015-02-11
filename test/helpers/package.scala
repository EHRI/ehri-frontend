
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
    val accounts: AccountManager = SqlAccountManager()
    mocks.users.map { case (profile, account) =>
      val acc = Await.result(accounts.create(account), 1.second)
    }
    mocks.oAuth2Associations.map { assoc =>
      Await.result(accounts.oAuth2.addAssociation(assoc.id, assoc.providerId, assoc.provider), 1.second)
    }
    mocks.openIDAssociations.map { assoc =>
      Await.result(accounts.openId.addAssociation(assoc.id, assoc.url), 1.second)
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
