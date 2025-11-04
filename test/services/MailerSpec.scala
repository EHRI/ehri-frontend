package services

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.mailer.{Email, MailerClient}
import play.api.test.{PlaySpecification, WithApplication}

class MailerSpec extends PlaySpecification {

  val app: Application = new GuiceApplicationBuilder().build()

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
