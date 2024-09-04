package services

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.mailer.{Email, MailerClient}
import play.api.test.{PlaySpecification, WithApplication}

class MailerSpec extends PlaySpecification {

  val app: Application = new GuiceApplicationBuilder()
    .configure(
      "play.mailer.host" -> "localhost",
      // NB: SMTP_PORT is set in the travis config to 2500
      "play.mailer.port" -> scala.util.Properties.envOrElse("SMTP_PORT", "25")
    ).build()

  "SMTP mailer" should {
    "send mails" in new WithApplication(app) {
      val mailer = this.app.injector.instanceOf[MailerClient]
      val email = Email(
        "Test Email",
        "Test FROM <test@example.com>",
        Seq("Test TO <test@example.com>"))
      mailer.send(email)
      success
    }
  }
}
