package helpers

import backend.test.RestBackendRunner
import play.api.test.{PlaySpecification, WithApplication}

/**
 * Abstract specification which initialises an instance of the
 * Neo4j server with the EHRI endpoint and cleans/sets-up the
 * test data before and after every test.
 */
abstract class Neo4jRunnerSpec() extends PlaySpecification with RestBackendRunner with UserFixtures with TestConfiguration {
  sequential

  def config = Map("neo4j.server.port" -> testPort) ++ getConfig

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  case class FakeApp(specificConfig: Map[String,Any] = Map.empty) extends WithApplication(
    fakeApplication(additionalConfiguration = config ++ specificConfig, global = getGlobal))

  override def before = {
    super[RestBackendRunner].before
    super[UserFixtures].before
  }

}
