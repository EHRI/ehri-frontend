package controllers.institutions

import java.time.Instant
import java.util.UUID

import actors.harvesting.OaiPmhHarvesterManager.{OaiPmhHarvestData, OaiPmhHarvestJob}
import actors.harvesting.{OaiPmhHarvester, OaiPmhHarvesterManager}
import akka.actor.Props
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.FileStage
import javax.inject._
import models.HarvestEvent.HarvestEventType
import models._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import services.harvesting.{HarvestEventService, OaiPmhClient, OaiPmhConfigService, OaiPmhError}
import services.storage.FileStorage

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class OaiPmhConfigs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  oaipmhConfigs: OaiPmhConfigService,
  oaiPmhClient: OaiPmhClient,
  harvestEvents: HarvestEventService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  private val logger: Logger = Logger(classOf[OaiPmhConfigs])


  def get(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    oaipmhConfigs.get(id, ds).map { opt =>
      Ok(Json.toJson(opt))
    }
  }

  def save(id: String, ds: String): Action[OaiPmhConfig] = EditAction(id).async(parse.json[OaiPmhConfig]) { implicit request =>
    oaipmhConfigs.save(id, ds, request.body).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    oaipmhConfigs.delete(id, ds).map { r =>
      Ok(Json.toJson(r))
    }
  }

  def test(id: String, ds: String): Action[OaiPmhConfig] = EditAction(id).async(parse.json[OaiPmhConfig]) { implicit request =>
    val getIdentF = oaiPmhClient.identify(request.body)
    val listIdentF = oaiPmhClient.listIdentifiers(request.body)
    (for (ident <- getIdentF; _ <- listIdentF)
      yield Ok(Json.toJson(ident))).recover {
      case e: OaiPmhError => BadRequest(Json.obj("error" -> e.errorMessage))
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def harvest(id: String, ds: String, fromLast: Boolean): Action[OaiPmhConfig] = EditAction(id).async(parse.json[OaiPmhConfig]) { implicit request =>
    val lastHarvest: Future[Option[Instant]] =
      if (fromLast) harvestEvents.get(id, Some(ds)).map(events =>
        events
          .filter(_.eventType == HarvestEventType.Completed)
          .map(_.created)
          .lastOption
      ) else immediate(Option.empty[Instant])

    lastHarvest.map { last =>
      val endpoint = request.body
      val jobId = UUID.randomUUID().toString
      val data = OaiPmhHarvestData(endpoint, prefix = prefix(id, ds, FileStage.Input), from = last)
      val job = OaiPmhHarvestJob(id, ds, jobId, data = data)
      mat.system.actorOf(Props(OaiPmhHarvesterManager(job, oaiPmhClient, storage, harvestEvents)), jobId)

      Ok(Json.obj(
        "url" -> controllers.admin.routes.Tasks
          .taskMonitorWS(jobId).webSocketURL(conf.https),
        "jobId" -> jobId
      ))
    }
  }

  def cancelHarvest(id: String, jobId: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    import scala.concurrent.duration._
    mat.system.actorSelection("user/" + jobId).resolveOne(5.seconds).map { ref =>
      logger.info(s"Monitoring job: $jobId")
      ref ! OaiPmhHarvester.Cancel
      Ok(Json.obj("ok" -> true))
    }.recover {
      case e => InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }
}
