package controllers.admin

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import utils.WebsocketConstants

import scala.util.{Failure, Success}


@Singleton
case class Tasks @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  private def logger = Logger(this.getClass)
  import scala.concurrent.duration._

  object MessageHandler {
    def props: Props = Props(new MessageHandler)
  }

  class MessageHandler extends Actor {
    private val logger = play.api.Logger(getClass)

    override def receive: Receive = {
      case e => logger.warn(s"Unhandled message: $e")
    }
  }

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]

  def taskMonitorWS(jobId: String): WebSocket = AuthenticatedWebsocket(_.account.exists(_.staff)) { implicit request =>
    ActorFlow.actorRef { out =>
      system.actorSelection(s"user/$jobId").resolveOne(5.seconds).onComplete {
        case Success(ref) =>
          logger.info(s"Monitoring job: $jobId")
          ref ! out
        case Failure(_) =>
          logger.warn(s"Unable to find task with ID: $jobId")
          out ! s"${WebsocketConstants.ERR_MESSAGE}: No running task found with ID: $jobId."
      }

      MessageHandler.props
    }
  }
}
