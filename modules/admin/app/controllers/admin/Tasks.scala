package controllers.admin

import actors.LongRunningJob
import org.apache.pekko.actor.{Actor, ActorLogging, ActorNotFound, ActorSystem, Props}
import org.apache.pekko.stream.{Materializer, OverflowStrategy}
import controllers.AppComponents
import controllers.base.AdminController
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import utils.WebsocketConstants

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success}


@Singleton
case class Tasks @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  import scala.concurrent.duration._

  object MessageHandler {
    def props: Props = Props(new MessageHandler)
  }

  class MessageHandler extends Actor with ActorLogging {
    private val logger = play.api.Logger(getClass)

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      super.preRestart(reason, message)
      log.error(reason, "Unhandled exception for message: {}", message)
    }

    override def receive: Receive = {
      case e => logger.warn(s"Unhandled message: $e")
    }
  }

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]

  def taskMonitorWS(jobId: String): WebSocket = AuthenticatedWebsocket(_.account.exists(_.staff)) { implicit request =>
    logger.debug(s"Opening websocket for task: $jobId")
    ActorFlow.actorRef(out => {
      system.actorSelection(s"user/$jobId").resolveOne(5.seconds).onComplete {
        case Success(ref) =>
          logger.info(s"Monitoring job: $jobId")
          ref ! out
        case Failure(_) =>
          logger.warn(s"Unable to find task with ID: $jobId")
          out ! s"${WebsocketConstants.ERR_MESSAGE}: No running task found with ID: $jobId."
      }

      MessageHandler.props
    }, 24, OverflowStrategy.dropTail)
  }

  def cancel(jobId: String): Action[AnyContent] = WithUserAction.async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      logger.info(s"Cancelling job: $jobId")
      ref ! LongRunningJob.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      // Check if job is already cancelled or missing...
      case _: ActorNotFound => Ok(Json.obj("ok" -> JsNull));
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }
}
