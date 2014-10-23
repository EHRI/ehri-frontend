package mocks

import com.typesafe.plugin.MailerBuilder

case class MockMail(to: List[String], from: List[String], text: String, html: String)

/**
 * Mock mailer plugin that buffers results. The default one
 * just send output the the log.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockBufferedMailer(mailBuffer: collection.mutable.ListBuffer[MockMail]) extends MailerBuilder {
  def send(bodyText: String, bodyHtml: String): Unit = {
    mailBuffer += MockMail(e("recipients"), e("from"), bodyText, bodyHtml)
  }
}

