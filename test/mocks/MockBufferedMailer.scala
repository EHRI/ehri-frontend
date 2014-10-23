package mocks

import com.typesafe.plugin.MailerBuilder
import com.typesafe.plugin.CommonsMailerPlugin

case class MockMail(to: List[String], from: List[String], text: String, html: String)

/**
 * Mock mailer plugin that buffers results. The default one
 * just send output the the log.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockBufferedMailer() extends MailerBuilder {

  val mailBuffer = collection.mutable.ListBuffer.empty[MockMail]

  def send(bodyText: String, bodyHtml: String): Unit = {
    mailBuffer += MockMail(e("recipients"), e("from"), bodyText, bodyHtml)
  }
}

