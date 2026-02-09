package controllers.datasets

import actors.harvesting.Harvester.HarvestJob
import actors.harvesting.OaiPmhHarvester.{OaiPmhHarvestData, OaiPmhHarvestJob}
import actors.harvesting.ResourceSyncHarvester.{ResourceSyncData, ResourceSyncJob}
import actors.harvesting.UrlSetHarvester.{UrlSetHarvesterData, UrlSetHarvesterJob}
import actors.harvesting.{HarvesterManager, OaiPmhHarvester, ResourceSyncHarvester, UrlSetHarvester}
import org.apache.pekko.actor.{ActorContext, ActorRef, Props}
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import controllers.generic.Update
import models.HarvestEvent.HarvestEventType
import models._
import play.api.http.HeaderNames
import play.api.i18n.Messages
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient, WSRequest}
import play.api.mvc._
import services.datasets.ImportDatasetService
import services.harvesting._
import services.storage.FileStorage

import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

@Singleton
case class HarvestConfigs @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  datasets: ImportDatasetService,
  oaipmhConfigs: OaiPmhConfigService,
  oaiPmhClient: OaiPmhClient,
  rsConfigs: ResourceSyncConfigService,
  rsClient: ResourceSyncClient,
  urlSetConfigs: UrlSetConfigService,
  ws: WSClient,
  harvestEvents: HarvestEventService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

  private val allowedTypes = config.get[Seq[String]]("ehri.admin.dataManager.inputTypes")

  case class DatasetRequest[A](
    item: Repository,
    userOpt: Option[UserProfile],
    dataset: ImportDataset,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  private def DatasetAction(id: String, ds: String): ActionBuilder[DatasetRequest, AnyContent] =
    EditAction(id) andThen new CoreActionTransformer[EditRequest, DatasetRequest] {
      override protected def transform[A](request: EditRequest[A]): Future[DatasetRequest[A]] = {
        implicit val req: EditRequest[A] = request
        datasets.get(id, ds).map { dataset =>
          DatasetRequest(req.item, req.userOpt, dataset, req)
        }
      }
    }

  private def prettyJsonErrors(e: JsError)(implicit req: RequestHeader): String = e.errors.map { case (path, errors) =>
    s"${path.path.headOption.map(_.toString).getOrElse("?")}: ${errors.headOption.map(v => Messages(v.message)).getOrElse("")}"
  }.mkString("; ")

  private def dataError(dataset: ImportDataset): Future[Result] =
    immediate(BadRequest(Json.obj("error" -> s"unsupported config type or data for ${dataset.src}")))

  private def jsonError(dataset: ImportDataset, e: JsError)(implicit req: RequestHeader): Future[Result] =
    immediate(BadRequest(Json.obj("error" -> s"unable to parse config for type '${dataset.src}' ${prettyJsonErrors(e)}")))

  private def withCheckedPayload(config: JsObject, dataset: ImportDataset)(f: HarvestConfig => Future[Result])(implicit req: RequestHeader): Future[Result] = {
    dataset.src match {
      case ImportDataset.Src.OaiPmh => config.validate[OaiPmhConfig] match {
        case JsSuccess(oaiPmhConfig: OaiPmhConfig, _) => f(oaiPmhConfig)
        case e@JsError(_) => jsonError(dataset, e)
      }
      case ImportDataset.Src.Rs => config.validate[ResourceSyncConfig] match {
        case JsSuccess(rsConfig: ResourceSyncConfig, _) => f(rsConfig)
        case e@JsError(_) => jsonError(dataset, e)
      }
      case ImportDataset.Src.UrlSet => config.validate[UrlSetConfig] match {
        case JsSuccess(urlSetConfig: UrlSetConfig, _) => f(urlSetConfig)
        case e@JsError(_) => jsonError(dataset, e)
      }
      case _ => immediate(BadRequest(Json.obj("error" -> "unsupported dataset type")))
    }
  }

  def get(id: String, ds: String): Action[AnyContent] = DatasetAction(id, ds).async { implicit request =>
    val configF: Future[Option[HarvestConfig]] = request.dataset.src match {
      case ImportDataset.Src.OaiPmh => oaipmhConfigs.get(id, ds)
      case ImportDataset.Src.Rs => rsConfigs.get(id, ds)
      case ImportDataset.Src.UrlSet => urlSetConfigs.get(id, ds)
      case _ => immediate(Option.empty[HarvestConfig])
    }
    configF.map(r => Ok(Json.toJson(r)))
  }

  def save(id: String, ds: String): Action[JsObject] = DatasetAction(id, ds).async(apiJson[JsObject]) { implicit request =>
    withCheckedPayload(request.body, request.dataset) {
      case c: OaiPmhConfig => oaipmhConfigs.save(id, ds, c).map(r => Ok(Json.toJson(r)))
      case c: ResourceSyncConfig => rsConfigs.save(id, ds, c).map(r => Ok(Json.toJson(r)))
      case c: UrlSetConfig => urlSetConfigs.save(id, ds, c).map(r => Ok(Json.toJson(r)))
      case _ => dataError(request.dataset)
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = DatasetAction(id, ds).async { implicit request =>
    request.dataset.src match {
      case ImportDataset.Src.OaiPmh => oaipmhConfigs.delete(id, ds).map(_ => NoContent)
      case ImportDataset.Src.Rs => rsConfigs.delete(id, ds).map(_ => NoContent)
      case ImportDataset.Src.UrlSet => urlSetConfigs.delete(id, ds).map(_ => NoContent)
      case _ => dataError(request.dataset)
    }
  }

  def clean(id: String, ds: String): Action[JsObject] = DatasetAction(id, ds).async(apiJson[JsObject]) { implicit request =>
    withCheckedPayload(request.body, request.dataset) {
      case c: ResourceSyncConfig => cleanRs(id, ds, c)
      case c: UrlSetConfig => cleanUrlSet(id, ds, c)
      case _ => dataError(request.dataset)
    }
  }

  def test(id: String, ds: String): Action[JsObject] = DatasetAction(id, ds).async(apiJson[JsObject]) { implicit request =>
    withCheckedPayload(request.body, request.dataset) {
      case c: OaiPmhConfig => testOaiPmh(c)
      case c: ResourceSyncConfig => testRs(c)
      case c: UrlSetConfig => testUrlSet(c)
      case _ => dataError(request.dataset)
    }
  }

  def harvest(id: String, ds: String, fromLast: Boolean): Action[JsObject] = DatasetAction(id, ds).async(apiJson[JsObject]) { implicit request =>
    withCheckedPayload(request.body, request.dataset) {
      case c: OaiPmhConfig => harvestOaiPmh(id, ds, c, fromLast)
      case c: ResourceSyncConfig => harvestRs(id, ds, c)
      case c: UrlSetConfig => harvestUrlSet(id, ds, c)
      case _ => dataError(request.dataset)
    }
  }

  private def testOaiPmh(config: OaiPmhConfig)(implicit messages: Messages): Future[Result] = {
    val getIdentF = oaiPmhClient.identify(config)
    val listIdentF = oaiPmhClient.listIdentifiers(config)
    (for (ident <- getIdentF; _ <- listIdentF)
      yield Ok(Json.toJson(ident))).recover {
      case e: OaiPmhError => BadRequest(Json.obj("error" -> e.errorMessage))
      case e =>
        e.printStackTrace()
        InternalServerError(Json.obj("error" -> e.getMessage))

    }
  }

  private def testRs(config: ResourceSyncConfig): Future[Result] = {
    authReq(config.url, config.auth).head().map { r =>
      checkRemoteFile(r).fold(Ok(Json.obj("ok" -> true))) { err =>
        BadRequest(Json.obj("error" -> err))
      }
    }
  }

  private def testUrlSet(config: UrlSetConfig): Future[Result] = {
    // First, check for duplicate file names...
    if (config.duplicates.nonEmpty) {
      val seq = config.duplicates.map(p => s"(${p._1+1}, ${p._2+1})").mkString(", ")
      immediate(BadRequest(Json.obj("error" -> s"Duplicate file names at rows: $seq")))
    } else {
      // Check all rows response to a HEAD request with the right info...
      def req(url: String): Future[Option[(String, String)]] = try {
        authReq(url, auth = config.auth, headers = config.headers).head().map { r =>
          checkRemoteFile(r).map(err => url -> err)
        }
      } catch {
        // Invalid URL
        case e: IllegalArgumentException => immediate(Some(url -> e.getMessage))
      }

      val errs: Seq[Future[Option[(String, String)]]] = config.urls.map(um => req(um.url))
      val s: Future[Option[String]] = Future.sequence(errs).map { errs =>
        errs.collectFirst {
          case Some((url, err)) => s"$url: $err"
        }
      }

      s.map { errOpt =>
        errOpt.fold(Ok(Json.obj("ok" -> true))) { err =>
          BadRequest(Json.obj("error" -> err))
        }
      }
    }
  }

  private def cleanRs(id: String, ds: String, config: ResourceSyncConfig)(implicit req: RequestHeader): Future[Result] = {
    val pre = prefix(id, ds, FileStage.Input)
    val remoteF: Future[Set[String]] = rsClient.list(config).map { links =>
      links.map(item => Uri(item.loc).path.dropChars(1).toString).toSet
    }

    cleanFiles(pre, remoteF).map { diff => Ok(Json.toJson(diff)) }.recover {
      case e: ResourceSyncError => BadRequest(Json.obj("error" -> e.errorMessage))
    }
  }

  private def cleanUrlSet(id: String, ds: String, config: UrlSetConfig)(implicit req: RequestHeader): Future[Result] = {
    val pre = prefix(id, ds, FileStage.Input)
    val remoteF: Future[Set[String]] = immediate(config.urls.map(_.name).toSet)

    cleanFiles(pre, remoteF).map { diff => Ok(Json.toJson(diff)) }.recover {
      case e: Exception => BadRequest(Json.obj("error" -> e.getMessage))
    }
  }

  private def harvestRs(id: String, ds: String, endpoint: ResourceSyncConfig)(implicit req: RequestHeader, userOpt: Option[UserProfile]): Future[Result] = immediate {
    val jobId = UUID.randomUUID().toString
    val data = ResourceSyncData(endpoint, prefix = prefix(id, ds, FileStage.Input))
    val job = ResourceSyncJob(id, ds, jobId, data = data)
    val init = (context: ActorContext) => context.actorOf(Props(ResourceSyncHarvester(rsClient, storage)))
    submitJob(jobId, job, init)
  }

  private def harvestOaiPmh(id: String, ds: String, config: OaiPmhConfig, fromLast: Boolean)(implicit req: RequestHeader, userOpt: Option[UserProfile]): Future[Result] = {
    val lastHarvest: Future[Option[Instant]] =
      if (fromLast) harvestEvents.get(id, Some(ds)).map(events =>
        events
          .filter(_.eventType == HarvestEventType.Completed)
          .map(_.created)
          .lastOption
      ) else immediate(Option.empty[Instant])

    lastHarvest.map { last =>
      val jobId = UUID.randomUUID().toString
      val data = OaiPmhHarvestData(config, prefix = prefix(id, ds, FileStage.Input), from = last)
      val job = OaiPmhHarvestJob(id, ds, jobId, data = data)
      val init = (context: ActorContext) => context.actorOf(Props(OaiPmhHarvester(oaiPmhClient, storage)))
      submitJob(jobId, job, init)
    }
  }

  private def harvestUrlSet(id: String, ds: String, config: UrlSetConfig)(implicit req: RequestHeader, userOpt: Option[UserProfile]): Future[Result] = immediate {
    val jobId = UUID.randomUUID().toString
    val data = UrlSetHarvesterData(config, prefix = prefix(id, ds, FileStage.Input))
    val job = UrlSetHarvesterJob(id, ds, jobId, data = data)
    val init = (context: ActorContext) => context.actorOf(Props(UrlSetHarvester(ws, storage)))
    submitJob(jobId, job, init)
  }

  private def submitJob(jobId: String, job: HarvestJob, init: ActorContext => ActorRef)(implicit userOpt: Option[UserProfile], req: RequestHeader): Result =  {
    mat.system.actorOf(Props(HarvesterManager(job, init, harvestEvents)), jobId)

    Ok(Json.obj(
      "url" -> controllers.admin.routes.Tasks
        .taskMonitorWS(jobId).webSocketURL(conf.https),
      "jobId" -> jobId
    ))
  }

  private def cleanFiles(pre: String, remoteF: Future[Set[String]]): Future[Set[String]] = {
    val storedF: Future[Set[String]] = storage.streamFiles(Some(pre))
      .map(fm => fm.key.replace(pre, ""))
      .runWith(Sink.seq)
      .map(_.toSet)

    for { stored <- storedF; remote <- remoteF}
      yield stored -- remote
  }

  private def authReq(url: String, auth: Option[BasicAuthConfig], headers: Option[Seq[(String, String)]] = None): WSRequest = {
    val withHeaders = headers.fold(ws.url(url)) { headers =>
      ws.url(url).withHttpHeaders(headers:_*)
    }
    auth.fold(withHeaders) {
        case BasicAuthConfig(username, password) => withHeaders.withAuth(username, password, WSAuthScheme.BASIC) }
  }
    

  private def checkRemoteFile(r: WSRequest#Response): Option[String] = {
    if (r.status != 200)
      Some(s"Unexpected HTTP response status code: ${r.status}")
    else if (r.header(HeaderNames.CONTENT_LENGTH).map(_.toLong).getOrElse(0L) <= 0)
      Some("Content is of zero or unknown length (possibly chunked encoding)")
    else if (!allowedTypes.exists(r.contentType.toLowerCase.startsWith)) {
      Some(s"Unknown or unexpected content type: '${r.contentType}'")
    } else None
  }
}
