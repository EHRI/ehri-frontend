import models.AccountDAO
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
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
   * @param app
   * @return
   */
  def loadFixtures(implicit app: play.api.Application) = {
    val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get
    mocks.userFixtures.map { case (profile, account) =>
      userDAO.create(account.email, account.profile_id)
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
  abstract class WithFixures(val app: FakeApplication = FakeApplication()) extends Around with Scope {
    implicit def implicitApp = app
    override def around[T: AsResult](t: => T): Result = {
      running(app) {
        loadFixtures
        AsResult(t)
      }
    }
  }
}
