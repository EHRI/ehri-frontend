package utils

import play.api.libs.mailer.{Email, MailerClient}

/**
 * Mock mailer plugin that buffers results. Sending to
 * `failureDomain` addresses fails.
 */
case class MockBufferedMailer(mailBuffer: collection.mutable.ListBuffer[Email]) extends MailerClient {
  def send(email: Email): String = {
    if (email.to.exists(_.endsWith("@" + MockBufferedMailer.failureDomain)))
      throw new RuntimeException(s"Mock mail failure sending to: ${email.to.mkString(", ")}")
    mailBuffer += email
    email.subject
  }
}

object MockBufferedMailer {
  val failureDomain = "mail-failure.invalid"
}
