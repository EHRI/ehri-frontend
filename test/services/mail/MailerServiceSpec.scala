package services.mail

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.specs2.specification.AfterAll
import play.api.libs.mailer.Email
import play.api.test.PlaySpecification
import utils.MockBufferedMailer

class MailerServiceSpec extends PlaySpecification with AfterAll {

  private val actorSystem = ActorSystem("mailer-service-spec", ConfigFactory.parseString(
    "contexts.mailer.executor = thread-pool-executor"))

  override def afterAll(): Unit = actorSystem.terminate()

  private def service: (MailerService, collection.mutable.ListBuffer[Email]) = {
    val buffer = collection.mutable.ListBuffer.empty[Email]
    AsyncMailerService(MockBufferedMailer(buffer), actorSystem) -> buffer
  }

  private val email = Email(
    subject = "Test",
    from = "EHRI <help@example.com>",
    to = Seq("user@example.com"),
    bodyText = Some("Hello")
  )

  "AsyncMailerService" should {
    "send email" in {
      val (mailer, buffer) = service
      await(mailer.send(email)) must beTrue
      buffer.map(_.to) must equalTo(Seq(Seq("user@example.com")))
    }

    "not fail when sending fails" in {
      val (mailer, buffer) = service
      await(mailer.send(email.copy(to = Seq("user@" + MockBufferedMailer.failureDomain)))) must beFalse
      buffer must beEmpty
    }

    "keep subjects to a single line" in {
      val (mailer, buffer) = service
      await(mailer.send(email.copy(subject = "Test\r\nBcc: someone@example.com"))) must beTrue
      buffer.map(_.subject) must equalTo(Seq("Test Bcc: someone@example.com"))
    }
  }
}
