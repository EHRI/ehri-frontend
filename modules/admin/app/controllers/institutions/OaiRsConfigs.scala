package controllers.institutions

import java.util.UUID

import actors.harvesting.OaiRsHarvesterManager.{OaiRsHarvestData, OaiRsHarvestJob}
import actors.harvesting.{OaiRsHarvester, OaiRsHarvesterManager}
import akka.actor.Props
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.FileStage
import javax.inject._
import models._
import play.api.Logger
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.harvesting.{HarvestEventService, OaiRsClient}
import services.storage.FileStorage


@Singleton
case class OaiRsConfigs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  rsClient: OaiRsClient,
  ws: WSClient,
  harvestEvents: HarvestEventService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  private val logger: Logger = Logger(classOf[OaiRsConfigs])

  def test(id: String, ds: String): Action[OaiRsConfig] = EditAction(id).async(parse.json[OaiRsConfig]) { implicit request =>
    ws.url(request.body.changeList.toString).head().map { r =>
      if (r.status != 200)
        BadRequest(Json.obj("error" -> s"Unexpected status: ${r.status}"))
      else if (r.header(HeaderNames.CONTENT_LENGTH).map(_.toLong).getOrElse(0L) <= 0)
        BadRequest(Json.obj("error" -> "Changelist is of zero or unknown length"))
      else if (!r.header(HeaderNames.CONTENT_TYPE).forall(s => s == "text/xml" || s == "application/xml"))
        BadRequest(Json.obj("error" -> s"Unexpected content type"))
      else
        Ok(Json.toJson("ok" -> true))
    }
  }


  def sync(id: String, ds: String): Action[OaiRsConfig] = EditAction(id).apply(parse.json[OaiRsConfig]) { implicit request =>
      val endpoint = request.body
      val jobId = UUID.randomUUID().toString
      val data = OaiRsHarvestData(endpoint, bucket, prefix = prefix(id, ds, FileStage.Input))
      val job = OaiRsHarvestJob(id, ds, jobId, data = data)
      mat.system.actorOf(Props(OaiRsHarvesterManager(job, ws, rsClient, storage, harvestEvents)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks
          .taskMonitorWS(jobId).webSocketURL(globalConfig.https),
        "jobId" -> jobId
      ))
  }

  def cancelSync(id: String, jobId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      logger.info(s"Monitoring job: $jobId")
      ref ! OaiRsHarvester.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }
}
