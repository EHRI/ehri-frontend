package helpers

import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.test.FakeApplication
import play.api.test.Helpers._


/**
 * Run a spec after loading the given resource name as SQL fixtures.
 */
class WithSqlFile(val resource: String, val app: FakeApplication = FakeApplication()) extends Around with Scope {
  implicit def implicitApp = app
  override def around[T: AsResult](t: => T): Result = {
    running(app) {
      loadSqlResource(resource)
      AsResult.effectively(t)
    }
  }
}
