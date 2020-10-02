package services.ingest

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.fasterxml.jackson.databind.JsonMappingException
import defines.{ContentTypes, EntityType}
import javax.inject.Inject
import play.api.http.HeaderNames
import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json._
import play.api.libs.ws.{BodyWritable, SourceBody, WSClient}
import play.api.{Configuration, Logger}
import services.data.Constants
import services.redirects.MovedPageLookup
import services.search.{SearchConstants, SearchIndexMediator}
import services.storage.FileStorage
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
  pageRelocator: MovedPageLookup,
  fileStorage: FileStorage
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
    implicit val writes: Writes[IngestResult] = Writes {
      case i: ImportLog => Json.toJson(i)(ImportLog.format)
      case i: SyncLog => Json.toJson(i)(SyncLog.format)
      case i: ErrorLog => Json.toJson(i)(ErrorLog.format)
    }
    implicit val format: Format[IngestResult] = Format(reads, writes)
  }

  // The result of a regular import
  case class ImportLog(
    createdKeys: Map[String, Seq[String]],
    created: Int,
    updatedKeys: Map[String, Seq[String]],
    updated: Int,
    unchangedKeys: Map[String, Seq[String]],
    unchanged: Int,
    message: Option[String] = None,
    errors: Map[String, String] = Map.empty
  ) extends IngestResult

  object ImportLog {
    implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(SnakeCase)
    implicit val format: Format[ImportLog] = Json.format[ImportLog]
  }

  // The result of an EAD synchronisation, which incorporates an import
  case class SyncLog(deleted: Seq[String], created: Seq[String], moved: Map[String, String], log: ImportLog) extends IngestResult

  object SyncLog {
    implicit val format: Format[SyncLog] = Json.format[SyncLog]
  }

  // An import error we can understand, e.g. not a crash!
  case class ErrorLog(error: String, details: String) extends IngestResult

  object ErrorLog {
    implicit val format: Format[ErrorLog] = Json.format[ErrorLog]
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
        val cancellable = context.system.scheduler.scheduleAtFixedRate(500.millis, 1000.millis, self, Ticker.Run)
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

  private def storeManifestAndLog(job: IngestJob, res: Either[String, IngestResult]): Future[URI] = {
    import play.api.libs.json._
    val out: JsValue = res match {
      case Left(s) => JsString(s)
      case Right(r: IngestResult) => Json.toJson(r)
    }

    val data = Json.obj(
      "id" -> job.id,
      "type" -> job.data.dataType,
      "params" -> Json.obj(
        "scope" -> job.data.params.scope,
        "lang" -> job.data.params.lang,
        "fonds" -> job.data.params.fonds,
        "allow-update" -> job.data.params.allowUpdate,
        "handler" -> job.data.params.handler,
        "importer" -> job.data.params.importer,
        "excludes" -> job.data.params.excludes,
        "properties" -> job.data.params.properties.nonEmpty,
        "commit" -> job.data.params.commit
      ),
      "user" -> job.data.user.toOption,
      "stats" -> out
    )

    val bytes = Source.single(ByteString.fromArray(
      Json.prettyPrint(data).getBytes(StandardCharsets.UTF_8)))
    val classifier = config.get[String]("storage.ingest.classifier")
    val time = ZonedDateTime.now()
    val now = time.format(DateTimeFormatter.ofPattern("uMMddHHmmss"))
    val path = s"ingest-logs/ingest-$now-${job.id}.json"
    fileStorage.putBytes(classifier, path, bytes, public = true)
  }

  // Create 301 redirects for items that have moved URLs
  private def remapMovedUnits(movedIds: Seq[(String, String)]): Future[Int] = {
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

    pageRelocator.addMoved(remapUrlsFromPrefixes(movedIds, prefixes))
  }

  // Re-index the scope in which the ingest was run
  private def reindex(scopeType: ContentTypes.Value, id: String)(implicit chan: ActorRef): Future[Unit] = {
    msg(s"Reindexing... $scopeType $id", chan)
    indexer.clearKeyValue(SearchConstants.HOLDER_ID, id).flatMap { _ =>
      msg(s"Cleared ${SearchConstants.HOLDER_ID}: $id", chan)
      indexer.indexChildren(EntityType.withName(scopeType.toString), id)
    }
  }

  // Run the actual data ingest on the backend
  private def handleIngest(job: IngestJob): Future[Either[String, IngestResult]] = {
    import scala.concurrent.duration._

    // We need to pass the server a reference of the properties file
    // which it can read, which involves copying it.
    // NB: Hack that assumes the server is on the same
    // host and we really shouldn't do this!
    val props: Option[java.nio.file.Path] = job.data.params.properties.map { propTmp =>
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
        params.lang.map(LANG -> _).toSeq ++
        params.fonds.map(FONDS -> _).toSeq ++
        params.handler.map(HANDLER -> _).toSeq ++
        params.importer.map(IMPORTER -> _).toSeq ++
        params.excludes.map(EXCLUDES -> _) ++
        props.map(PROPERTIES_FILE -> _.toAbsolutePath.toString)
    }


    val bodyWritable: BodyWritable[Path] =
        BodyWritable(file => SourceBody(FileIO.fromPath(file)), job.data.contentType)

    logger.info(s"Dispatching ingest: ${job.data.params}")
    val upload = ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/import/${job.data.dataType}")
      .withRequestTimeout(Duration.Inf)
      .addHttpHeaders(job.data.user.toOption.map(Constants.AUTH_HEADER_NAME -> _).toSeq: _*)
      .addHttpHeaders(HeaderNames.CONTENT_TYPE -> job.data.contentType)
      .addQueryStringParameters(wsParams(job.data.params): _*)
      .post(job.data.params.file.map(_.path)
        .getOrElse(sys.error("Unexpectedly empty ingest data!")))(bodyWritable)
      .map { r =>
        logger.trace(r.body)
        logger.debug(s"Ingest WS status: ${r.status}")
        try Right(r.json.as[IngestResult]) catch {
          case _: JsonMappingException | _: JsResultException => Left(r.body)
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
    msg(s"Data: created: ${log.created}, updated: ${log.updated}, unchanged: ${log.unchanged}, errors: ${log.errors.size}", chan)
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

    val mainTask: Future[Either[String, IngestResult]] = handleIngest(job)

    val ticker: ActorRef = actorSystem.actorOf(Ticker.props)
    ticker ! (chan -> "Ingesting")
    mainTask.onComplete { _ =>
      ticker ! Ticker.Stop
    }

    val uploadLog: Future[Unit] = mainTask.flatMap { out =>
      msg("Uploading log...", chan)
      storeManifestAndLog(job, out).map { url =>
        msg(s"Log stored at $url", chan)
      } recover {
        case e =>
          logger.error("Unable to upload ingest log:", e)
          msg(s"Error uploading ingest log: ${e.getMessage}", chan)
      }
    }

    val ingestTasks: Future[Unit] = mainTask.flatMap {
      case Right(result) => result match {
        case log: ImportLog => handleIngestResult(job, log)(chan)
        case log: SyncLog => handleSyncResult(job, log)(chan)
        case err: ErrorLog => handleError(job, err)(chan)
      }
      case Left(errorString) =>
        msg(errorString, chan)
        immediate(())
    }

    // Do uploading and result handling asynchronously
    val allTasks: Future[Unit] = for {
      _ <- uploadLog
      r <- ingestTasks
    } yield r

    allTasks.onComplete {
      case Failure(e) =>
        logger.error("Ingest error: ", e)
        msg(WebsocketConstants.ERR_MESSAGE, chan)
      case _ => msg(WebsocketConstants.DONE_MESSAGE, chan)
    }

    allTasks
  }
}
