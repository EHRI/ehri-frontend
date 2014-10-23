package backend.rest

import play.api.test.PlaySpecification
import controllers.base.FakeApp
import backend.IdGenerator

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class IdGeneratorSpec extends PlaySpecification {

  "IdGenerator" should {
    "give a valid first ID from a list of strings non numeric strings" in new FakeApp {
      IdGenerator.nextNumericId(Seq("foo", "bar")) must equalTo(1)
    }

    "give a valid ID from a list of numeric strings" in  new FakeApp {
      IdGenerator.nextNumericId(Seq("foo3", "bar1")) must equalTo(4)
    }
  }
}
