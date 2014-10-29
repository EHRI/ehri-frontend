package helpers

import backend.test.RestBackendRunner
import play.api.test.{PlaySpecification, WithApplication}

/**
 * Abstract specification which initialises an instance of the
 * Neo4j server with the EHRI endpoint and cleans/sets-up the
 * test data before and after every test.
 */
trait IntegrationTestRunner extends PlaySpecification with RestBackendRunner with UserFixtures with TestConfiguration {
  sequential

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  case class ITestApp(specificConfig: Map[String,Any] = Map.empty) extends WithApplication(
    fakeApplication(additionalConfiguration = backendConfig ++ getConfig ++ specificConfig, global = getGlobal))

  // NB: Because both UserFixtures and the RestBackendendRunner
  // do stuff before each test we need to break the ambiguity of
  // which goes first.
  override def before = {
    super[RestBackendRunner].before
    super[UserFixtures].before
  }
}
