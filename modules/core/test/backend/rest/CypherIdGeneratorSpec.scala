package backend.rest

import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class CypherIdGeneratorSpec extends PlaySpecification {
  "Cypher IdGenerator" should {
    "give a valid first ID from a list of strings non numeric strings" in {
      val idGen = new CypherIdGenerator("%06d")
      idGen.nextNumericId(Seq("foo", "bar")) must equalTo(1)
    }

    "give a valid ID from a list of numeric strings" in {
      val idGen = new CypherIdGenerator("%06d")
      idGen.nextNumericId(Seq("foo3", "bar1")) must equalTo(4)
    }
  }
}
