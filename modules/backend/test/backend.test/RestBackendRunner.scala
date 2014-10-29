package backend.test

import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeExample, Step, Fragments}
import eu.ehri.extension.test.helpers.ServerRunner
import eu.ehri.extension.AbstractAccessibleEntityResource

/**
 * Specs2 magic to provide equivalent of JUnit's beforeClass/afterClass.
 */
trait BeforeAllAfterAll extends Specification with BeforeExample {
  // see http://bit.ly/11I9kFM (specs2 User Guide)
  override def map(fragments: =>Fragments) =
    Step(beforeAll()) ^ fragments ^ Step(afterAll())

  protected def beforeAll()
  protected def afterAll()
}


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestBackendRunner extends BeforeAllAfterAll {

  val testPort = 7575

  val runner = ServerRunner.getInstance(testPort,
    classOf[AbstractAccessibleEntityResource[_]].getPackage.getName, "ehri")

  /**
   * Tear down and setup fixtures before every test
   */
  def before = {
    runner.tearDownData()
    runner.setUpData()
  }

  /**
   * Start the server before every class test
   */
  def beforeAll() = runner.start()

  /**
   * Stop the server after every class test
   */
  def afterAll() = runner.stop()

}
