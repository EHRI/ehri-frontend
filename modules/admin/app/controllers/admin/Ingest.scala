package controllers.admin

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import defines.EntityType
import models.base.AnyModel
import play.api.Logger
import play.api.data.Form
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import services.data.AuthenticatedUser
import services.ingest.IngestApi.{IngestData, IngestJob}
import services.ingest.{IngestApi, IngestParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.util.{Failure, Success}


@Singleton
case class Ingest @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ingestApi: IngestApi
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  private def logger = Logger(this.getClass)
  import scala.concurrent.duration._

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]

  // Actor that runs the actual index tasks
  case class IngestActor() extends Actor {

    override def receive: Receive = waiting

    def waiting: Receive = {
      case job: IngestJob => context.become(run(job))
    }

    def run(job: IngestJob): Receive = {
      case chan: ActorRef => ingestApi.run(job, chan).onComplete { _ =>
        // Terminate the actor...
        context.stop(self)
      }
    }
  }

  def ingestPost(scopeType: EntityType.Value, scopeId: String, dataType: String, fonds: Option[String]): Action[MultipartFormData[TemporaryFile]] = AdminAction(parse.multipartFormData(Int.MaxValue)).async { implicit request =>

    def showErrorForm(form: Form[IngestParams]): Future[Result] = {
      val scopeItemF: Future[AnyModel] = userDataApi.getAny[AnyModel](scopeId)
      val fondsItemF: Future[Option[AnyModel]] = fonds
        .map(id => userDataApi.getAny[AnyModel](id)
          .map(item => Some(item))).getOrElse(Future.successful(None))

      for (scopeItem <- scopeItemF; fondsItem <- fondsItemF)
        yield BadRequest(views.html.admin.utils.ingest(scopeItem, fondsItem, form,
          controllers.admin.routes.Ingest.ingestPost(scopeType, scopeId, dataType, fonds)))
    }

    val boundForm = IngestParams.ingestForm.bindFromRequest()
    request.body.file(IngestParams.DATA_FILE).map { data =>
      boundForm.fold(
        errForm => showErrorForm(errForm),
        ingestTask => {

          // Tag this task with a unique ID...
          val jobId = UUID.randomUUID().toString

          // We only want XML types here, everything else is just binary
          val contentType = data.contentType.filter(_.endsWith("xml"))
            .getOrElse(play.api.http.ContentTypes.BINARY)

          // Save the properties file, if given, to a temp file on the server.
          // NB: Overcomplicated due to https://github.com/playframework/playframework/issues/6203
          val props: Option[TemporaryFile] = request.body
              .file(IngestParams.PROPERTIES_FILE)
              .filter(_.filename.nonEmpty)
              .map(_.ref)

          val task = ingestTask.copy(properties = props, file = Some(data.ref))
          val ingest = IngestData(task, dataType, contentType, AuthenticatedUser(request.user.id))

          val runner = system.actorOf(Props(IngestActor()), jobId)
          runner ! IngestJob(jobId, ingest)
          logger.info(s"Submitted ingest job: $jobId")

          immediate {
            if (isAjax) Ok(Json.obj(
              "url" -> controllers.admin.routes.Ingest.ingestMonitorWS(jobId).webSocketURL(globalConfig.https),
              "jobId" -> jobId
            ))
            else Redirect(controllers.admin.routes.Ingest.ingestMonitor(jobId))
          }
        }
      )
    }.getOrElse(showErrorForm(boundForm.withError(IngestParams.DATA_FILE, "required")))
  }

  def ingestMonitor(jobId: String): Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.ingest.ingestMonitor(controllers.admin.routes.Ingest.ingestMonitorWS(jobId)))
  }

  def ingestMonitorWS(jobId: String): WebSocket = AdminWebsocket { implicit request =>
    ActorFlow.actorRef { out =>
      system.actorSelection("user/" + jobId).resolveOne(5.seconds).onComplete {
        case Success(ref) =>
          logger.info(s"Monitoring job: $jobId")
          ref ! out
        case Failure(_) =>
          logger.warn(s"Unable to find ingest job: $jobId")
          out ! s"No running job found with id: $jobId."
      }

      Props(IngestActor())
    }
  }
}
