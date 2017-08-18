package controllers.admin

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import controllers.AppComponents
import controllers.base.AdminController
import defines.EntityType
import models.UserProfile
import models.admin.IngestParams
import models.base.AnyModel
import play.api.Logger
import play.api.data.Form
import play.api.http.{HeaderNames, HttpVerbs}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsResultException, JsValue, Json, Reads}
import play.api.libs.streams.ActorFlow
import play.api.libs.ws.WSClient
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import services.data.Constants
import services.search.{SearchConstants, SearchIndexMediator}
import utils.WebsocketConstants

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.util.{Failure, Success}

@Singleton
case class Ingest @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient
)(implicit system: ActorSystem, mat: Materializer) extends AdminController {

  private def logger = Logger(this.getClass)
  import scala.concurrent.duration._

  private implicit val messageTransformer: MessageFlowTransformer[JsValue, String] =
    MessageFlowTransformer.jsonMessageFlowTransformer[JsValue, String]


  // Actor that just prints out a progress indicator
  object Ticker {
    def props = Props(new Ticker())
    case object Stop
    case object Run
  }

  case class Ticker() extends Actor {
    private val states = Vector("|", "/", "-", "\\")

    override def receive: Receive = init

    def init: Receive = {
      case (actorRef: ActorRef, msg: String) =>
        val cancellable = context.system.scheduler.schedule(500.millis, 1000.millis, self, Ticker.Run)
        context.become(tick(actorRef, msg, 0, cancellable))
    }

    def tick(actorRef: ActorRef, msg: String, state: Int, cancellable: Cancellable): Receive = {
      case Ticker.Run =>
        actorRef ! s"$msg... ${states(state)}"
        context.become(tick(actorRef, msg, if (state < 3) state + 1 else 0, cancellable))
      case Ticker.Stop => cancellable.cancel()
    }
  }

  // Actor that runs the actual index tasks
  object IngestActor {
    case class IngestData(
      dataType: String,
      params: IngestParams,
      ct: String,
      file: java.io.File,
      user: UserProfile
    )

    // A job with a given ID tag
    case class IngestJob(id: String, data: IngestData)

    // A result from the import endpoint
    sealed trait IngestResult
    object IngestResult {
      implicit val reads: Reads[IngestResult] = Reads { json =>
        json.validate[ErrorLog].orElse(json.validate[ImportLog].orElse(json.validate[SyncLog]))
      }
    }

    // The result of a regular import
    case class ImportLog(created: Int, updated: Int, unchanged: Int, message: Option[String] = None) extends IngestResult
    object ImportLog {
      implicit val reads: Reads[ImportLog] = Json.reads[ImportLog]
    }

    // The result of an EAD synchronisation, which incorporates an import
    case class SyncLog(deleted: Seq[String], created: Seq[String], moved: Map[String, String], log: ImportLog) extends IngestResult
    object SyncLog {
      implicit val reads: Reads[SyncLog] = Json.reads[SyncLog]
    }

    // An import error we can understand, e.g. not a crash!
    case class ErrorLog(error: String, details: String) extends IngestResult
    object ErrorLog {
      implicit val reads: Reads[ErrorLog] = Json.reads[ErrorLog]
    }

    private def msg(s: String, chan: ActorRef): Unit = {
      logger.info(s)
      chan ! s
    }

    // Get an indexer handle with our our channel and filtered output
    private def indexer(implicit chan: ActorRef) =
      searchIndexer.handle.withChannel(chan, filter = _ % 1000 == 0)

    // Create 301 redirects for items that have moved URLs
    private def remapMovedUnits(movedIds: Seq[(String, String)])(implicit chan: ActorRef): Future[Int] = {
      def remapUrlsFromPrefixes(items: Seq[(String, String)], prefixes: Seq[String]): Seq[(String, String)] = {
        def enc(s: String) = java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        items.flatMap { case (from, to) =>
          prefixes.map(p => s"$p${enc(from)}" -> s"$p${enc(to)}")
        }
      }
      // Bit of a hack. Use the reverse routes to get relative URLs
      // with a fake ID, then remove the ID.
      val prefixes = Seq(
        controllers.portal.routes.DocumentaryUnits.browse("TEST").url,
        controllers.units.routes.DocumentaryUnits.get("TEST").url
      ).map(_.replace("TEST", ""))

      appComponents.pageRelocator.addMoved(remapUrlsFromPrefixes(movedIds, prefixes)).map { num =>
        msg(s"Relocated $num item(s)", chan)
        num
      }
    }

    // Re-index the scope in which the ingest was run
    private def reindex(entityType: EntityType.Value, id: String)(implicit chan: ActorRef): Future[Unit] = {
      msg(s"Reindexing... $entityType $id", chan)
      indexer.clearKeyValue(SearchConstants.HOLDER_ID, id).flatMap { _ =>
        indexer.indexChildren(entityType, id)
      }
    }

    // Run the actual data ingest on the backend
    private def handleIngest(data: IngestData): Future[Either[String, IngestResult]] = {
      import scala.concurrent.duration._

      logger.info(s"Dispatching ingest: ${data.params}")
      ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/import/${data.dataType}")
        .withRequestTimeout(20.minutes)
        .addHttpHeaders(Constants.AUTH_HEADER_NAME -> data.user.id)
        .addHttpHeaders(HeaderNames.CONTENT_TYPE -> data.ct)
        .addQueryStringParameters(data.params.toParams: _*)
        .withMethod(HttpVerbs.POST)
        .withBody(data.file)
        .stream()
        .flatMap { r =>
        //logger.debug(s"Ingest response: ${r.body}")
        logger.info(s"Got status from ingest of: ${r.status}")
        r.bodyAsSource.runFold(ByteString.empty)(_ ++ _).map{ s =>
          logger.info("Parsing data...")
          try {
            Right(Json.parse(s.toArray).as[IngestResult])
          } catch {
            case (_: JsonParseException | _: JsResultException) => Left(s.utf8String)
          }
        }
      } recover {
        case e: Throwable =>
          logger.error("Error running ingest", e)
          Left(e.getMessage)
      }
    }

    // If we ran a sync job, handle moving and reindexing
    private def handleSyncResult(job: IngestJob, sync: SyncLog)(implicit chan: ActorRef): Future[Unit] = {
      msg("Received a valid sync manifest...", chan)
      handleIngestResult(job, sync.log).andThen { case _ =>
        msg(s"  Sync: deleted: ${sync.deleted.size}, moved: ${sync.moved.size}", chan)
        if (job.data.params.commit) {
          if (sync.moved.nonEmpty) {
            remapMovedUnits(sync.moved.toSeq)
          } else {
            msg("No reindexing necessary", chan)
            immediate(())
          }
        } else {
          msg("Task was a dry run so not creating redirects", chan)
          immediate(())
        }
      }
    }

    // Handle reindexing if item have changed
    private def handleIngestResult(job: IngestJob, log: ImportLog)(implicit chan: ActorRef): Future[Unit] = {
      msg(s"  Data: created: ${log.created}, updated: ${log.updated}, unchanged: ${log.unchanged}", chan)
      if (job.data.params.commit) {
        if (log.created > 0 || log.updated > 0) {
          reindex(job.data.params.scopeType, job.data.params.scope)
        } else {
          msg("No reindexing necessary", chan)
          immediate(())
        }
      } else {
        msg("Task was a dry run so not proceeding to reindex", chan)
        immediate(())
      }
    }

    private def handleError(job: IngestJob, err: ErrorLog)(implicit chan: ActorRef): Future[Unit] = {
      msg(s"${WebsocketConstants.ERR_MESSAGE}: ${err.details}", chan)
      immediate(())
    }
  }

  case class IngestActor() extends Actor {

    import IngestActor._

    override def receive: Receive = waiting

    def waiting: Receive = {
      case job: IngestJob => context.become(run(job))
    }

    def run(job: IngestJob): Receive = {
      case chan: ActorRef =>
        msg(s"Initialising ingest for job: ${job.id}...", chan)

        val mainTask: Future[Either[String, IngestResult]] = handleIngest(job.data)

        val ticker = system.actorOf(Ticker.props)
        ticker ! (chan -> "Ingesting")
        mainTask.onComplete { _ =>
          ticker ! Ticker.Stop
        }

        val allTasks = mainTask.flatMap {
          case Right(result) => result match {
            case log: ImportLog => handleIngestResult(job, log)(chan)
            case log: SyncLog => handleSyncResult(job, log)(chan)
            case err: ErrorLog => handleError(job, err)(chan)
          }
          case Left(errorString) =>
            msg(errorString, chan)
            immediate(())
        }

        allTasks.onComplete { _ =>
          msg(WebsocketConstants.DONE_MESSAGE, chan)
        }

        // Terminate the actor...
        context.stop(self)
    }
  }

  def ingestPost(scopeType: EntityType.Value, scopeId: String, dataType: String, fonds: Option[String]): Action[MultipartFormData[TemporaryFile]] =
    AdminAction(parse.multipartFormData(Int.MaxValue)).async { implicit request =>

      def showForm(form: Form[IngestParams]): Future[Result] = {
        val scopeItemF: Future[AnyModel] = userDataApi.getAny[AnyModel](scopeId)
        val fondsItemF: Future[Option[AnyModel]] = fonds
          .map(id => userDataApi.getAny[AnyModel](id)
            .map(item => Some(item))).getOrElse(Future.successful(None))

        for {
          scopeItem <- scopeItemF
          fondsItem <- fondsItemF
        } yield BadRequest(views.html.admin.utils
          .ingest(scopeItem, fondsItem, form,
            controllers.admin.routes.Ingest.ingestPost(scopeType, scopeId, dataType, fonds)))
      }

    val boundForm = IngestParams.ingestForm.bindFromRequest()
    request.body.file(IngestParams.DATA_FILE).map { data =>
      boundForm.fold(
        errForm => showForm(errForm),
        ingestTask => {

          // Tag this task with a unique ID...
          val jobId = UUID.randomUUID().toString

          // We only want XML types here, everything else is just binary
          val ct = data.contentType.filter(_.endsWith("xml"))
            .getOrElse(play.api.http.ContentTypes.BINARY)

          // Save the properties file, if given, to a temp file on the server.
          // NB: Overcomplicated due to https://github.com/playframework/playframework/issues/6203
          val props: Option[java.io.File] = request.body
              .file(IngestParams.PROPERTIES_FILE)
              .filter(_.filename.nonEmpty)
              .map { f =>
            val propsTmp: java.io.File = File.createTempFile(s"ingest-$jobId", ".properties")
            propsTmp.deleteOnExit()
            f.ref.moveTo(propsTmp)
            propsTmp
          }

          val task = ingestTask.copy(properties = props)
          val ingest = IngestActor.IngestData(dataType, task, ct, data.ref.path.toFile, request.user)

          val runner = system.actorOf(Props(IngestActor()), jobId)
          runner ! IngestActor.IngestJob(jobId, ingest)
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
    }.getOrElse(showForm(boundForm.withError(IngestParams.DATA_FILE, "required")))
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
