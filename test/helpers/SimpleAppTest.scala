package helpers

import org.specs2.specification.AfterAll
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * Run a set of tests with a simple app configuration
  */
abstract class SimpleAppTest extends AfterAll {

  protected val implicitApp: Application = GuiceApplicationBuilder().build();

  // make sure we shut down the app after all tests have run
  override def afterAll: Unit = implicitApp.stop()
}
