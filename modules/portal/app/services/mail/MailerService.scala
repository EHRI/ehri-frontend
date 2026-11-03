package services.mail

import com.google.inject.ImplementedBy
import org.apache.pekko.actor.ActorSystem
import play.api.Logger
import play.api.libs.mailer.{Email, MailerClient}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[AsyncMailerService])
trait MailerService {
  /**
    * Send an email. The returned future always succeeds: with true
    * if the email was sent, or false if sending failed (which is logged.)
    */
  def send(email: Email): Future[Boolean]
}

@Singleton
case class AsyncMailerService @Inject()(mailer: MailerClient, actorSystem: ActorSystem) extends MailerService {
  private val logger = Logger(getClass)
  private implicit val ec: ExecutionContext = actorSystem.dispatchers.lookup("contexts.mailer")

  override def send(email: Email): Future[Boolean] = {
    val safe = email.copy(subject = email.subject.replaceAll("[\\r\\n]+", " "))
    Future {
      mailer.send(safe)
      true
    }.recover {
      case NonFatal(e) =>
        logger.error(s"Unable to send email '${safe.subject}' to ${safe.to.size} recipient(s)", e)
        false
    }
  }
}
