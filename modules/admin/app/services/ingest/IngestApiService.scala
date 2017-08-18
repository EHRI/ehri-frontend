package services.ingest

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import javax.inject.Inject

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import com.fasterxml.jackson.databind.JsonMappingException
import defines.EntityType
import play.api.http.HeaderNames
import play.api.libs.json.{JsResultException, Json, Reads}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
import services.data.Constants
import services.redirects.MovedPageLookup
import services.search.{SearchConstants, SearchIndexMediator}
import utils.WebsocketConstants

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure


/**
  * Service class for ingesting XML data into the database backend.
  *
  */
case class IngestApiService @Inject()(
  config: Configuration,
  ws: WSClient,
  searchIndexer: SearchIndexMediator,
  pageRelocator: MovedPageLookup
)(implicit actorSystem: ActorSystem, mat: Materializer) extends IngestApi {

  import services.ingest.IngestApi._
  import scala.concurrent.duration._


  private implicit val ec: ExecutionContext = mat.executionContext
  private val logger = Logger(getClass)

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

    pageRelocator.addMoved(remapUrlsFromPrefixes(movedIds, prefixes)).map { num =>
      num
    }
  }

  // Re-index the scope in which the ingest was run
  private def reindex(entityType: EntityType.Value, id: String)(implicit chan: ActorRef): Future[Unit] = {
    msg(s"Reindexing... $entityType $id", chan)
    indexer.clearKeyValue(SearchConstants.HOLDER_ID, id).flatMap { _ =>
      msg(s"Cleared ${SearchConstants.HOLDER_ID}: $id", chan)
      indexer.indexChildren(entityType, id)
    }
  }

  // Run the actual data ingest on the backend
  private def handleIngest(data: IngestData): Future[Either[String, IngestResult]] = {
    import scala.concurrent.duration._

    // We need to pass the server a reference of the properties file
    // which it can read, which involves copying it.
    // NB: Hack that assumes the server is on the same
    // host and we really shouldn't do this!
    val props: Option[java.nio.file.Path] = data.params.properties.map { propTmp =>
      import scala.collection.JavaConverters._
      val readTmp = Files.createTempFile(s"ingest", ".properties")
      propTmp.moveTo(readTmp, replace = true)
      val perms = Set(
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE)
      Files.setPosixFilePermissions(readTmp, perms.asJava)
      readTmp
    }

    def wsParams(params: IngestParams): Seq[(String, String)] = {
      import IngestParams._
      Seq(
        SCOPE -> params.scope,
        TOLERANT -> params.tolerant.toString,
        ALLOW_UPDATE -> params.allowUpdate.toString,
        LOG -> params.log,
        COMMIT -> params.commit.toString) ++
        params.fonds.map(FONDS -> _).toSeq ++
        params.handler.map(HANDLER -> _).toSeq ++
        params.importer.map(IMPORTER -> _).toSeq ++
        params.excludes.map(EXCLUDES -> _) ++
        props.map(PROPERTIES_FILE -> _.toAbsolutePath.toString)
    }

    logger.info(s"Dispatching ingest: ${data.params}")
    val upload = ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/import/${data.dataType}")
      .withRequestTimeout(20.minutes)
      .addHttpHeaders(data.user.toOption.map(Constants.AUTH_HEADER_NAME -> _).toSeq: _*)
      .addHttpHeaders(HeaderNames.CONTENT_TYPE -> data.contentType)
      .addQueryStringParameters(wsParams(data.params): _*)
      .post(data.params.file.map(_.toFile)
        .getOrElse(sys.error("Unexpectedly empty ingest data!")))
      .map { r =>
        logger.debug(s"Ingest WS status: ${r.status}")
        try Right(r.json.as[IngestResult]) catch {
          case (_: JsonMappingException | _: JsResultException) => Left(r.body)
        }
      } recover {
      case e: Throwable =>
        logger.error("Error running ingest", e)
        Left(s"Error running ingest: ${e.getMessage}")
    }

    upload.onComplete { _ =>
      // Delete properties temp file...
      props.foreach(f => f.toFile.delete())
    }

    upload
  }

  // If we ran a sync job, handle moving and reindexing
  private def handleSyncResult(job: IngestJob, sync: SyncLog)(implicit chan: ActorRef): Future[Unit] = {
    msg("Received a valid sync manifest...", chan)
    handleIngestResult(job, sync.log).flatMap { _ =>
      msg(s"Sync: moved: ${sync.moved.size}, new: ${sync.created.size}, deleted: ${sync.deleted.size}", chan)
      if (job.data.params.commit) {
        if (sync.moved.nonEmpty) {
          msg("Creating redirects...", chan)
          remapMovedUnits(sync.moved.toSeq).map { num =>
            msg(s"Relocated $num item(s)", chan)
          }
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
    msg(s"Data: created: ${log.created}, updated: ${log.updated}, unchanged: ${log.unchanged}", chan)
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

  override def run(job: IngestJob, chan: ActorRef): Future[Unit] = {

    msg(s"Initialising ingest for job: ${job.id}...", chan)

    val mainTask: Future[Either[String, IngestResult]] = handleIngest(job.data)

    val ticker: ActorRef = actorSystem.actorOf(Ticker.props)
    ticker ! (chan -> "Ingesting")
    mainTask.onComplete { _ =>
      ticker ! Ticker.Stop
    }

    val allTasks: Future[Unit] = mainTask.flatMap {
      case Right(result) => result match {
        case log: ImportLog => handleIngestResult(job, log)(chan)
        case log: SyncLog => handleSyncResult(job, log)(chan)
        case err: ErrorLog => handleError(job, err)(chan)
      }
      case Left(errorString) =>
        msg(errorString, chan)
        immediate(())
    }

    allTasks.onComplete {
      case Failure(e) =>
        logger.error("Ingest error: ", e)
        msg(WebsocketConstants.ERR_MESSAGE, chan)
      case _ => msg(WebsocketConstants.DONE_MESSAGE, chan)
    }

    allTasks
  }
}
