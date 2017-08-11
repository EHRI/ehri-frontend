package controllers.admin

import java.util.UUID
import javax.inject._

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import services.search._

import scala.concurrent.Future.{successful => immediate}
import scala.util.{Failure, Success}

@Singleton
case class IngestUtils @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  private def logger = Logger(this.getClass)
  import scala.concurrent.duration._

  private implicit val messageTransformer = MessageFlowTransformer
    .jsonMessageFlowTransformer[JsValue, String]

  object IngestActor {
    def props = Props(new IngestActor())
  }

  class IngestActor() extends Actor {

    override def postStop(): Unit = {
      super.postStop()
      println("Stopped!")
    }

    override def receive: Receive = waiting

    def waiting: Receive = {
      case job: String =>
        println(s"Got Job ID... $job")
        context.become(init(sender(), job))
    }

    def init(origin: ActorRef, id: String): Receive = {
      case monitor: ActorRef =>
        println("Started monitoring!")
        1.to(20).foreach { i =>
          monitor ! s"Tick: $id -> $i"
          Thread.sleep(100)
        }

        monitor ! "Done"

        context.stop(self)
    }
  }

  def ingest: Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.ingest.ingestForm())
  }

  def ingestPost: Action[AnyContent] = AdminAction.apply { implicit request =>

    val job = UUID.randomUUID().toString
    val runner = system.actorOf(IngestActor.props, job)
    runner ! job

    Redirect(controllers.admin.routes.IngestUtils.ingestMonitor(job))
  }

  def ingestMonitor(job: String): Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.ingest.ingestMonitor(controllers.admin.routes.IngestUtils.ingestMonitorWS(job)))
  }

  def ingestMonitorWS(job: String): WebSocket = WebSocket.acceptOrResult[JsValue, String] { implicit request =>

    authHandler.restoreAccount(request).flatMap {
      case (Some(account), _) => fetchProfile(account).flatMap {
        case Some(prof) if prof.isAdmin => immediate(Right {
          ActorFlow.actorRef { out =>

            system.actorSelection("user/" + job).resolveOne(5.seconds).onComplete {
              case Success(ref) => ref ! out
              case Failure(ex) => out ! "No job found"
            }

            IngestActor.props
          }
        })
        // user doesn't have a profile, or it's not admin
        case _ => authenticationFailed(request).map(r => Left(r))
      }
      case _ => authenticationFailed(request).map(r => Left(r))
    }
  }
}
