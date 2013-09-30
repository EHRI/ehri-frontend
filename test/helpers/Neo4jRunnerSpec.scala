package helpers

import org.specs2.mutable._
import org.specs2.specification.BeforeExample
import eu.ehri.extension.test.utils.ServerRunner
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import eu.ehri.extension.AbstractAccessibleEntityResource
import play.api.test.WithApplication

/**
 * Abstract specification which initialises an instance of the
 * Neo4j server with the EHRI endpoint and cleans/sets-up the
 * test data before and after every test.
 */
abstract class Neo4jRunnerSpec(cls: Class[_]) extends Specification with BeforeExample with TestMockLoginHelper {
  sequential

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)

  val runner: ServerRunner = ServerRunner.getInstance(cls.getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsPackages()
    .add(new ThirdPartyJaxRsPackage(
    classOf[AbstractAccessibleEntityResource[_]].getPackage.getName, "/ehri"));
  runner.start

  class FakeApp extends WithApplication(fakeApplication(additionalConfiguration = config, global = getGlobal))

  def before = {
    runner.tearDown
    runner.setUp
  }
}