package helpers

import org.specs2.specification.BeforeEach

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait UserFixtures extends BeforeEach {

  /**
   * Tear down and setup fixtures before every test
   */
  def before = {
    mockdata.accountFixtures = mockdata.users
  }
}
