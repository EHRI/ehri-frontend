package helpers

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeEach
import org.specs2.specification.core.Fragments
import eu.ehri.extension.test.helpers.ServerRunner
import eu.ehri.extension.SystemEventResource


/**
 * Specs2 magic to provide equivalent of JUnit's beforeClass/afterClass.
 */
trait BeforeAllAfterAll extends Specification with BeforeEach {
  // see http://bit.ly/11I9kFM (specs2 User Guide)
  override def map(fragments: => Fragments): Fragments =
    step(beforeAll()) ^ fragments ^ step(afterAll())

  protected def beforeAll()
  protected def afterAll()
}

object RestApiRunner {
  val testPort = 7575
  val testEndpoint = "ehri"

  val backendConfig: Map[String, Any] = Map(
    "neo4j.server.host" -> "localhost",
    "neo4j.server.port" -> testPort,
    "neo4j.server.endpoint" -> testEndpoint
  )
}

trait RestApiRunner extends BeforeAllAfterAll {

  import RestApiRunner._
  private val runner = ServerRunner.getInstance(testPort,
    classOf[SystemEventResource].getPackage.getName, testEndpoint)

  /**
   * Tear down and setup fixtures before every test
   */
  def before: Unit = {
    runner.tearDownData()
    runner.setUpData()
  }

  /**
   * Start the server before every class test
   */
  def beforeAll(): Unit = runner.start()

  /**
   * Stop the server after every class test
   */
  def afterAll(): Unit = runner.stop()
}
