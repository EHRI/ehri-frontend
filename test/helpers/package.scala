
import java.io.File
import java.sql.CallableStatement
import models.AccountDAO
import models.sql.{SqlAccount, OAuth2Association, OpenIDAssociation}
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.db.DB
import play.api.http.{ContentTypes, HeaderNames}
import play.api.test.FakeApplication
import play.api.test.Helpers._

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
  private def loadSqlFixtures(implicit app: play.api.Application) = {
    val userDAO: AccountDAO = SqlAccount
    mocks.users.map { case (profile, account) =>
      val acc = userDAO.create(account.id, account.email, verified = account.verified, staff = account.staff,
        allowMessaging = account.allowMessaging)
      OpenIDAssociation.addAssociation(acc, acc.id + "-openid-test-url")
      OAuth2Association.addAssociation(acc, acc.id + "1234", "google")
    }
  }

  /**
   * Run inside an application with fixtures loaded.
   *
   * NB: The situation with extending WithApplication in specs2 seems
   * to be... not so simple:
   *
   * https://github.com/etorreborre/specs2/issues/87
   *
   * So here I've basically copy-pasted WithApplication and added
   * extra work before it returns.
   */
  abstract class WithSqlFixtures(val app: FakeApplication) extends Around with Scope {
    implicit def implicitApp = app
    override def around[T: AsResult](t: => T): Result = {
      running(app) {
        loadSqlFixtures
        AsResult.effectively(t)
      }
    }
  }

  /**
   * Load a file containing SQL statements into the DB.
   */
  private def loadSqlResource(resource: String)(implicit app: FakeApplication) = DB.withConnection { conn =>
    val file = new File(getClass.getClassLoader.getResource(resource).toURI)
    val path = file.getAbsolutePath
    val statement: CallableStatement = conn.prepareCall(s"RUNSCRIPT FROM '$path'")
    statement.execute()
    conn.commit()
  }

  /**
   * Run a spec after loading the given resource name as SQL fixtures.
   */
  abstract class WithSqlFile(val resource: String, val app: FakeApplication = FakeApplication()) extends Around with Scope {
    implicit def implicitApp = app
    override def around[T: AsResult](t: => T): Result = {
      running(app) {
        loadSqlResource(resource)
        AsResult.effectively(t)
      }
    }
  }
}
