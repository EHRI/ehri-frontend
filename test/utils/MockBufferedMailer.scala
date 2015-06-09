package utils

import play.api.libs.mailer.{Email, MailerClient}

/**
 * Mock mailer plugin that buffers results. The default one
 * just send output the the log.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockBufferedMailer(mailBuffer: collection.mutable.ListBuffer[Email]) extends MailerClient {
  def send(email: Email): String = {
    mailBuffer += email
    email.subject
  }
}

