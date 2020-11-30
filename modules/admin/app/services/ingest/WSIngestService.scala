package services.ingest

import java.io.PrintWriter
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
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json._
import play.api.libs.ws.{BodyWritable, SourceBody, WSClient}
import play.api.{Configuration, Logger}
import services.data.Constants
import services.redirects.MovedPageLookup
import services.search.{SearchConstants, SearchIndexMediator}
import services.storage.FileStorage

import scala.concurrent.{ExecutionContext, Future}


/**
  * Service class for ingesting XML data into the database backend.
  *
  */
case class WSIngestService @Inject()(
  config: Configuration,
  ws: WSClient,
  searchIndexer: SearchIndexMediator,
  pageRelocator: MovedPageLookup,
  fileStorage: FileStorage,
)(implicit actorSystem: ActorSystem, mat: Materializer) extends IngestService {

  import services.ingest.IngestService._

  import scala.concurrent.duration._


  private implicit val ec: ExecutionContext = mat.executionContext
  private val logger = Logger(getClass)

  // Actor that just prints out a progress indicator
  object Ticker {
    def props: Props = Props(new Ticker())
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

  // Get an indexer handle with our our channel and filtered output
  private def indexer(chan: ActorRef) =
    searchIndexer.handle.withChannel(chan, filter = _ % 1000 == 0)

  override def storeManifestAndLog(job: IngestJob, res: IngestResult): Future[URI] = {
    import play.api.libs.json._

    val data = Json.obj(
      "id" -> job.id,
      "type" -> job.data.dataType,
      "params" -> job.data.params,
      "user" -> job.data.user.toOption,
      "stats" -> res,
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
  override def remapMovedUnits(movedIds: Seq[(String, String)]): Future[Int] = {
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
  override def reindex(scopeType: ContentTypes.Value, id: String, chan: ActorRef): Future[Unit] = {
    indexer(chan).clearKeyValue(SearchConstants.HOLDER_ID, id).flatMap { _ =>
      indexer(chan).indexChildren(EntityType.withName(scopeType.toString), id)
    }
  }

  // Re-index the scope in which the ingest was run
  override def reindex(ids: Seq[String], chan: ActorRef): Future[Unit] = {
    indexer(chan).indexIds(ids: _*)
  }

  override def clearIndex(ids: Seq[String], chan: ActorRef): Future[Unit] = {
    indexer(chan).clearIds(ids: _*)
  }

  // Run the actual data ingest on the backend
  override def importData(job: IngestJob): Future[IngestResult] = {
    import scala.concurrent.duration._

    // We need to pass the server a reference of the properties file
    // which it can read, which involves copying it.
    // NB: Hack that assumes the server is on the same
    // host and we really shouldn't do this!
    val propFile: Option[Path] = job.data.params.properties match {
      case FileProperties(f) => f.map { propTmp =>
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
      case UrlProperties(_) => None
    }

    val propStr: Option[String] = job.data.params.properties match {
      case FileProperties(_) => propFile.map(_.toAbsolutePath.toString)
      case UrlProperties(url) => Some(url)
    }

    val dataFile: Option[Path] = job.data.params.data match {
      case FilePayload(f) => f.map(_.toAbsolutePath)
      case UrlMapPayload(urls) =>
        val temp = SingletonTemporaryFileCreator.create("ingest", ".json")
        val writer = new PrintWriter(temp.path.toString, "UTF-8")
        writer.write(Json.stringify(Json.toJson(urls)))
        writer.close()
        Some(temp.path)
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
        propStr.map(PROPERTIES_FILE -> _)
    }

    val bodyWritable: BodyWritable[Path] =
      BodyWritable(file => SourceBody(FileIO.fromPath(file)), job.data.contentType)

    logger.info(s"Dispatching ingest: ${job.data.params}")
    val upload = ws.url(s"${utils.serviceBaseUrl("ehridata", config)}/import/${job.data.dataType}")
      .withRequestTimeout(Duration.Inf)
      .addHttpHeaders(job.data.user.toOption.map(Constants.AUTH_HEADER_NAME -> _).toSeq: _*)
      .addHttpHeaders(HeaderNames.CONTENT_TYPE -> job.data.contentType)
      .addQueryStringParameters(wsParams(job.data.params): _*)
      // FIXME: make a BodyWritable that can serialize the URL map without a temp file
      .post(dataFile.getOrElse(sys.error("Unexpectedly empty ingest data!")))(bodyWritable)
      .map { r =>
        logger.trace(r.body)
        logger.debug(s"Ingest WS status: ${r.status}")

        try r.json.as[IngestResult] catch {
          case _: JsonMappingException | _: JsResultException =>
            ErrorLog("Unexpected data received from ingest backend", r.body)
        }
      } recover {
      case e: Throwable =>
        logger.error("Error running ingest", e)
        ErrorLog(s"Unexpected error running ingest", e.getMessage)
    }

    upload.onComplete { _ =>
      // Delete properties temp file...
      propFile.foreach(f => f.toFile.delete())
    }

    upload
  }
}
