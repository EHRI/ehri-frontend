package helpers

import play.api.test.PlaySpecification

/**
 * Abstract specification which initialises an instance of the
 * Neo4j server with the EHRI endpoint and cleans/sets-up the
 * test data before and after every test.
 */
trait IntegrationTestRunner extends PlaySpecification with RestApiRunner with UserFixtures with TestConfiguration {
  sequential

  // NB: Because both UserFixtures and the RestBackendendRunner
  // do stuff before each test we need to break the ambiguity of
  // which goes first.
  override def before: Unit = {
    super[RestApiRunner].before
    super[UserFixtures].before
  }
}
