package controllers.admin

import java.util.UUID

import actors.IngestActor
import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import defines.ContentTypes
import javax.inject.{Inject, Singleton}
import models.base.Model
import play.api.Logger
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import services.data.AuthenticatedUser
import services.ingest.IngestApi.{IngestData, IngestJob}
import services.ingest.{IngestApi, IngestParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Ingest @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  ingestApi: IngestApi
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  private def logger = Logger(this.getClass)

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]

  def ingestPost(scopeType: ContentTypes.Value, scopeId: String, dataType: IngestApi.IngestDataType.Value, fonds: Option[String]): Action[MultipartFormData[TemporaryFile]] =
    AdminAction(parse.multipartFormData(Int.MaxValue)).async { implicit request =>

      def showErrorForm(form: Form[IngestParams]): Future[Result] = {
        val scopeItemF: Future[Model] = userDataApi.getAny[Model](scopeId)
        val fondsItemF: Future[Option[Model]] = fonds
          .map(id => userDataApi.getAny[Model](id)
            .map(item => Some(item))).getOrElse(Future.successful(None))

        for (scopeItem <- scopeItemF; fondsItem <- fondsItemF)
          yield BadRequest(views.html.admin.tools.ingest(scopeItem, fondsItem, form, dataType,
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

            val runner = system.actorOf(Props(IngestActor(ingestApi)), jobId)
            runner ! IngestJob(jobId, ingest)
            logger.info(s"Submitted ingest job: $jobId")

            immediate {
              if (isAjax) Ok(Json.obj(
                "url" -> controllers.admin.routes.Tasks.taskMonitorWS(jobId).webSocketURL(globalConfig.https),
                "jobId" -> jobId
              ))
              else Redirect(controllers.admin.routes.Ingest.ingestMonitor(jobId))
            }
          }
        )
      }.getOrElse(showErrorForm(boundForm.withError(IngestParams.DATA_FILE, "required")))
    }

  def ingestMonitor(jobId: String): Action[AnyContent] = AdminAction.apply { implicit request =>
    Ok(views.html.admin.tasks.taskMonitor(
      Messages("ingest.monitor"),
      controllers.admin.routes.Tasks.taskMonitorWS(jobId)))
  }
}
