package utils

import play.api.libs.mailer.Email
import services.mail.MailerService

import scala.concurrent.Future

/**
 * Mock mailer service that buffers emails synchronously. Sending to
 * `MockBufferedMailer.failureDomain` addresses fails.
 */
case class MockMailerService(mailBuffer: collection.mutable.ListBuffer[Email]) extends MailerService {
  override def send(email: Email): Future[Boolean] = Future.successful {
    if (email.to.exists(_.endsWith("@" + MockBufferedMailer.failureDomain))) false
    else {
      mailBuffer += email
      true
    }
  }
}
