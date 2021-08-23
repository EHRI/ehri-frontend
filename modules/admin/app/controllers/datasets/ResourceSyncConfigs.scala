package controllers.datasets

import actors.harvesting.ResourceSyncHarvester.{ResourceSyncData, ResourceSyncJob}
import actors.harvesting.{Harvester, HarvesterManager, ResourceSyncHarvester}
import akka.actor.{ActorContext, Props}
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Update
import models.{FileStage, Repository, ResourceSyncConfig}
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.harvesting.{HarvestEventService, ResourceSyncClient, ResourceSyncConfigService, ResourceSyncError}
import services.storage.FileStorage

import java.util.UUID
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.Future

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
    configs.delete(id, ds).map(_ => NoContent)
  }

  def test(id: String, ds: String): Action[ResourceSyncConfig] = WithUserAction.async(parse.json[ResourceSyncConfig]) { implicit request =>
    ws.url(request.body.url).head().map { r =>
      if (r.status != 200)
        BadRequest(Json.obj("error" -> s"Unexpected status: ${r.status}"))
      else if (r.header(HeaderNames.CONTENT_LENGTH).map(_.toLong).getOrElse(0L) <= 0)
        BadRequest(Json.obj("error" -> "Changelist is of zero or unknown length"))
      else if (!r.header(HeaderNames.CONTENT_TYPE).forall(s => s == "text/xml" || s == "application/xml"))
        BadRequest(Json.obj("error" -> s"Unexpected content type"))
      else
        Ok(Json.obj("ok" -> true))
    }
  }

  def sync(id: String, ds: String): Action[ResourceSyncConfig] = EditAction(id).apply(parse.json[ResourceSyncConfig]) { implicit request =>
    val endpoint = request.body
    val jobId = UUID.randomUUID().toString
    val data = ResourceSyncData(endpoint, prefix = prefix(id, ds, FileStage.Input))
    val job = ResourceSyncJob(id, ds, jobId, data = data)
    val init = (context: ActorContext) => context.actorOf(Props(ResourceSyncHarvester(rsClient, storage)))
    mat.system.actorOf(Props(HarvesterManager(job, init, harvestEvents)), jobId)

    Ok(Json.obj(
      "url" -> controllers.admin.routes.Tasks
        .taskMonitorWS(jobId).webSocketURL(conf.https),
      "jobId" -> jobId
    ))
  }

  def cancelSync(id: String, jobId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      ref ! Harvester.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def clean(id: String, ds: String): Action[ResourceSyncConfig] = WithUserAction.async(parse.json[ResourceSyncConfig]) { implicit request =>
    val pre = prefix(id, ds, FileStage.Input)
    val remoteF: Future[Set[String]] = rsClient.list(request.body).map { links =>
      links.map(item => Uri(item.loc).path.dropChars(1).toString).toSet
    }

    val storedF: Future[Set[String]] = storage.streamFiles(Some(pre))
      .map(fm => fm.key.replace(pre, ""))
      .runWith(Sink.seq)
      .map(_.toSet)

    val diffF = for { stored <- storedF; remote <- remoteF} yield stored -- remote
    diffF.map { diff => Ok(Json.toJson(diff)) }.recover {
      case e: ResourceSyncError => BadRequest(Json.obj("error" -> e.errorMessage))
    }
  }
}
