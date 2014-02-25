package mocks

import com.typesafe.plugin.{MailerPlugin, MailerBuilder}

case class MockMail(to: List[String], from: List[String], text: String, html: String)

/**
 * Mock mailer plugin that buffers results. The default one
 * just send output the the log.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case object MockBufferedMailer extends MailerBuilder {

  val mailBuffer = collection.mutable.ListBuffer.empty[MockMail]

  def send(bodyText: String, bodyHtml: String): Unit = {
    mailBuffer += MockMail(e("recipients"), e("from"), bodyText, bodyHtml)
  }
}

/**
 * WARNING: This is a little fragile. Unfortunately it's needed because
 * there's no way with the default plugin to send mails anywhere expect
 * the log.
 */
class MockBufferedMailerPlugin(app: play.api.Application) extends MailerPlugin {
  override def email = MockBufferedMailer
}
