package helpers

import org.specs2.mutable._
import org.specs2.specification.{BeforeExample, Fragments, Step}
import eu.ehri.extension.test.utils.ServerRunner
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import eu.ehri.extension.AbstractAccessibleEntityResource
import play.api.test.{PlaySpecification, WithApplication}

trait BeforeAllAfterAll extends Specification {
  // see http://bit.ly/11I9kFM (specs2 User Guide)
  override def map(fragments: =>Fragments) =
    Step(beforeAll()) ^ fragments ^ Step(afterAll())

  protected def beforeAll()
  protected def afterAll()
}

/**
 * Abstract specification which initialises an instance of the
 * Neo4j server with the EHRI endpoint and cleans/sets-up the
 * test data before and after every test.
 */
abstract class Neo4jRunnerSpec(cls: Class[_]) extends PlaySpecification with BeforeExample with BeforeAllAfterAll with TestConfiguration {
  sequential

  val testPort = 7575
  def config = Map("neo4j.server.port" -> testPort) ++ getConfig

  val runner: ServerRunner = ServerRunner.getInstance(cls.getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsPackages
    .add(new ThirdPartyJaxRsPackage(
    classOf[AbstractAccessibleEntityResource[_]].getPackage.getName, "/ehri"))

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  case class FakeApp(specificConfig: Map[String,Any] = Map.empty) extends WithApplication(
    fakeApplication(additionalConfiguration = config ++ specificConfig, global = getGlobal)
  )

  def before() = {
    runner.tearDown()
    runner.setUp()
  }

  def beforeAll() = runner.start()

  def afterAll() = runner.stop()
}
