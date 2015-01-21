package helpers

import org.specs2.specification.BeforeExample

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait UserFixtures extends BeforeExample {

  /**
   * Tear down and setup fixtures before every test
   */
  def before = {
    mocks.accountFixtures = mocks.users
  }
}
