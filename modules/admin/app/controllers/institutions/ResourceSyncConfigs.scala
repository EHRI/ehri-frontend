package controllers.institutions

import java.util.UUID

import actors.harvesting.ResourceSyncHarvesterManager.{ResourceSyncData, ResourceSyncJob}
import actors.harvesting.{ResourceSyncHarvester, ResourceSyncHarvesterManager}
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
import services.harvesting.{HarvestEventService, ResourceSyncClient, ResourceSyncConfigService}
import services.storage.FileStorage


@Singleton
case class ResourceSyncConfigs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  rsClient: ResourceSyncClient,
  ws: WSClient,
  configs: ResourceSyncConfigService,
  harvestEvents: HarvestEventService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  private val logger: Logger = Logger(classOf[ResourceSyncConfigs])

  def get(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    configs.get(id, ds).map { opt =>
      Ok(Json.toJson(opt))
    }
  }

  def save(id: String, ds: String): Action[ResourceSyncConfig] = EditAction(id).async(parse.json[ResourceSyncConfig]) { implicit request =>
    configs.save(id, ds, request.body).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    configs.delete(id, ds).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def test(id: String, ds: String): Action[ResourceSyncConfig] = EditAction(id).async(parse.json[ResourceSyncConfig]) { implicit request =>
    ws.url(request.body.url).head().map { r =>
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


  def sync(id: String, ds: String): Action[ResourceSyncConfig] = EditAction(id).apply(parse.json[ResourceSyncConfig]) { implicit request =>
      val endpoint = request.body
      val jobId = UUID.randomUUID().toString
      val data = ResourceSyncData(endpoint, bucket, prefix = prefix(id, ds, FileStage.Input))
      val job = ResourceSyncJob(id, ds, jobId, data = data)
      mat.system.actorOf(Props(ResourceSyncHarvesterManager(job, rsClient, storage, harvestEvents)), jobId)

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
      ref ! ResourceSyncHarvester.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }
}
