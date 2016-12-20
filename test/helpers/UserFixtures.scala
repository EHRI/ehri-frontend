package helpers

import org.specs2.specification.BeforeEach

trait UserFixtures extends BeforeEach {

  /**
   * Tear down and setup fixtures before every test
   */
  def before: Unit = {
    mockdata.accountFixtures = mockdata.users
  }
}
