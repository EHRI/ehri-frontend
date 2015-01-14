package helpers

import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.test.FakeApplication
import play.api.test.Helpers._

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
class WithSqlFixtures(val app: FakeApplication) extends Around with Scope {
  implicit def implicitApp = app
  override def around[T: AsResult](t: => T): Result = {
    running(app) {
      loadSqlFixtures
      AsResult.effectively(t)
    }
  }
}

