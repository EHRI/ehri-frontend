package helpers

import akka.actor._
import akka.testkit._
import org.specs2.specification.AfterAll

/* A tiny class that can be used as a Specs2 ‘context’. */
abstract class AkkaTestkitSpecs2Support extends TestKit(ActorSystem())
  with AfterAll
  with ImplicitSender {

  // make sure we shut down the actor system after all tests have run
  override def afterAll: Unit = shutdown()
}
