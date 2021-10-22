package services.search

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import config.{serviceAuth, serviceBaseUrl}
import eu.ehri.project.indexing.converter.impl.JsonConverter
import models.EntityType
import play.api.libs.json.Json
import play.api.{Configuration, Logger}
import services.data.Constants.{AUTH_HEADER_NAME, STREAM_HEADER_NAME}
import services.search.SearchConstants.{ITEM_ID, TYPE}

import java.io.StringWriter
import java.time
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


case class AkkaStreamsIndexMediator @Inject()(actorSystem: ActorSystem, mat: Materializer, config: Configuration, executionContext: ExecutionContext) extends
  SearchIndexMediator {
  override val handle: SearchIndexMediatorHandle = AkkaStreamsIndexMediatorHandle()(actorSystem, mat, config, executionContext)
}

case class AkkaStreamsIndexMediatorHandle(
  chan: Option[ActorRef] = None,
  processFunc: String => String = identity[String],
  progressFilter: Int => Boolean = _ % 100 == 0
)(implicit system: ActorSystem,
  mat: Materializer,
  config: Configuration,
  executionContext: ExecutionContext) extends SearchIndexMediatorHandle {

  override def withChannel(actorRef: ActorRef, formatter: String => String, filter: Int => Boolean = _ % 100 == 0): AkkaStreamsIndexMediatorHandle =
    copy(chan = Some(actorRef), processFunc = formatter, progressFilter = filter)

  import scala.collection.JavaConverters._
  import scala.concurrent.duration._

  private val logger = Logger(classOf[AkkaStreamsIndexMediator])

  private val dataBaseUrl: Uri = serviceBaseUrl("ehridata", config)
  private val dataAuth: Option[Authorization] = serviceAuth("ehridata", config)
    .map { case (un, pw) => headers.Authorization(BasicHttpCredentials(un, pw))}
  private val solrBaseUrl: Uri = serviceBaseUrl("solr", config) + "/update"
  private val jsonSupport = EntityStreamingSupport.json(Integer.MAX_VALUE)
  private val jsonConverter = new JsonConverter
  private val mapper = new ObjectMapper
  private val writer = mapper.writer().withDefaultPrettyPrinter()

  private val poolConfig: ConnectionPoolSettings = ConnectionPoolSettings(system)
      .withConnectionSettings(ClientConnectionSettings(system))
      .withIdleTimeout(Duration.Inf)
      .withResponseEntitySubscriptionTimeout(Duration.Inf)

  private def httpSinkFlow: Flow[(HttpRequest, Uri), (Try[HttpResponse], Uri), HostConnectionPool] = Http()
    .newHostConnectionPool(
      solrBaseUrl.authority.host.toString(), solrBaseUrl.authority.port, poolConfig)
    .named("http-sink-pool-flow")

  private def sinkFlow(uri: Uri): Sink[ByteString, Future[String]] = Flow[ByteString]
    .prefixAndTail(0)
    .map { case (_, byteSrc) =>
      val entity = HttpEntity(ContentTypes.`application/json`, byteSrc).withoutSizeLimit()
      HttpRequest(HttpMethods.POST, uri.toRelative).withEntity(entity) -> uri
    }
    .via(httpSinkFlow)
    .collect {
      case (Success(response), tag) => response
      case (Failure(e), tag) => throw e
    }
    .flatMapConcat(_.entity.withoutSizeLimit().dataBytes)
    .toMat(Sink.fold(ByteString.empty)(_ ++ _))(Keep.right)
    .mapMaterializedValue(_.map(_.utf8String))
    .named("http-sink-flow")

  private val bytesToJsonNode: Flow[ByteString, JsonNode, akka.NotUsed] = Flow[ByteString]
    .via(jsonSupport.framingDecoder)
    .map(bytes => mapper.readValue(bytes.toArray, classOf[JsonNode]))
    .named("bytes-to-json-node")

  private val jsonNodeToBytes: Flow[JsonNode, ByteString, akka.NotUsed] = Flow[JsonNode]
    .map(node => ByteString.fromArray(writer.writeValueAsBytes(node)))
    .named("json-node-to-bytes")

  private val jsonNodeToDoc: Flow[JsonNode, JsonNode, akka.NotUsed] = Flow[JsonNode]
    .mapConcat[JsonNode](n => jsonConverter.convert(n).asScala.toVector)
    .named("json-node-to-solr-doc")

  private val solrUri: Uri = solrBaseUrl.withQuery(Uri.Query("commit" -> "true"))

  private def entitiesToRequests(entityTypes: Seq[EntityType.Value]): List[HttpRequest] =
    entityTypes.map { et =>
      val uri = dataBaseUrl
        .withPath(Uri.Path(s"/ehri/classes/$et"))
        .withQuery(Query("limit" -> "-1"))
      HttpRequest(HttpMethods.GET, uri)
    }.toList

  private def idsToRequests(ids: Seq[String]): List[HttpRequest] = {
    val uri = dataBaseUrl.withPath(Uri.Path(s"/ehri/entities"))
    val entity = HttpEntity(ContentTypes.`application/json`, Json.stringify(Json.toJson(ids)))
    val req = HttpRequest(HttpMethods.POST, uri, entity = entity)
    List(req)
  }

  private def childrenToRequests(et: EntityType.Value, id: String): List[HttpRequest] = {
    List(HttpRequest(HttpMethods.GET, dataBaseUrl.withPath(Uri.Path(s"/ehri/classes/$et/$id/list"))
      .withQuery(Query("limit" -> "-1", "all" -> "true"))))
  }

  private def setCommonHeaders(reqs: List[HttpRequest]): List[(HttpRequest, Uri)] = reqs.map { r =>
    val headers = Seq(RawHeader(STREAM_HEADER_NAME, "true"), RawHeader(AUTH_HEADER_NAME, "admin")) ++ dataAuth.toSeq
    val req = r.withHeaders(headers: _*)
    req -> r.uri
  }

  private def humanReadableFormat(duration: java.time.Duration): String =
    duration
      .toString
      .substring(2)
      .replaceAll("(\\d[HMS])(?!$)", "$1 ")
      .toLowerCase

  private def index(requests: List[HttpRequest]): Future[Unit] = {
    // NB: ideally we would do this using a flow which takes a
    // stream of HttpRequest objects, i.e. the newHostConnectionPool(...)
    // However, in practice weird errors seem to result and although it's
    // technically more efficient, using Http().singleRequest seems more
    // reliable. FIXME: investigate why...

    val init = java.time.Instant.now()
    var count = 0

    val bytesFlow = Source(setCommonHeaders(requests))
      .map { case (r, uri) =>
        // TBD: mapAsync(1) doesn't work here, resulting in errors like
        // "Response entity was not subscribed after 1 second..."
        // Not sure why but this approach of flattening the future
        // into a stream, and then flattening that, seems to work
        // fine.
        val s: Future[Source[JsonNode, _]] = Http()
          .singleRequest(r, settings = poolConfig)
          .map { rs =>
            logger.debug(s"Response status from $uri: ${rs.status}")
            rs.entity.withoutSizeLimit().dataBytes.via(bytesToJsonNode)
          }
        // flatten the future into a stream of JsonNode objects
        Source.future(s).flatMapConcat(n => n)
      }
      .flatMapConcat(n => n) // flatten the streams of JsonNodes into one stream
      .via(jsonNodeToDoc)
      .map { node =>
        logger.trace(writer.writeValueAsString(node))
        count = count + 1
        if (progressFilter(count)) {
          chan.foreach(_ ! processFunc(s"Items processed: $count"))
        }
        node
      }
      .via(jsonNodeToBytes)
      .via(jsonSupport.framingRenderer)

    logger.debug(s"Running SOLR uri: $solrUri")
    bytesFlow.runWith(sinkFlow(solrUri)).map { _ =>
      val interval = time.Duration.between(init, java.time.Instant.now())
      val msg = s"Total items processed: $count in ${humanReadableFormat(interval)}"
      logger.debug(msg)
      chan.foreach(_ ! processFunc(msg))
    }
  }

  private def jsonDelete(queries: String*): String = {
    // NB: Using Jackson directly since play-json does not
    // support objects with repeated key names.
    val sw = new StringWriter()
    try {
      val g = writer.getFactory.createGenerator(sw)
      try {
        g.writeStartObject()
        queries.foreach { q =>
          g.writeFieldName("delete")
          g.writeStartObject()
          g.writeObjectField("query", q)
          g.writeEndObject()
        }
        g.writeEndObject()
        g.flush()
      } finally {
        g.close()
      }
      sw.flush()
      sw.toString
    } finally {
      sw.close()
    }
  }

  private def deleteByQuery(queries: String*): Future[Unit] = {
    val dq: String = jsonDelete(queries: _*)
    logger.debug(s"Solr delete: $dq")
    val bytes = Source.single(ByteString(dq))
    bytes.runWith(sinkFlow(solrUri)).map(r => logger.debug(s"Solr response: $r"))
  }

  override def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = {
    logger.debug(s"Indexing types: $entityTypes")
    index(entitiesToRequests(entityTypes))
  }

  override def indexIds(ids: String*): Future[Unit] = {
    logger.debug(s"Indexing IDs: $ids")
    index(idsToRequests(ids))
  }

  override def indexChildren(entityType: EntityType.Value, id: String): Future[Unit] = {
    logger.debug(s"Indexing children: $entityType:$id")
    index(childrenToRequests(entityType, id))
  }

  override def clearAll(): Future[Unit] =
    deleteByQuery(s"*:*")

  override def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] =
    deleteByQuery(entityTypes.map(et => s"$TYPE:$et"):_*)

  override def clearIds(ids: String*): Future[Unit] =
    deleteByQuery(ids.map(id => s"$ITEM_ID:$id"): _*)

  override def clearKeyValue(key: String, value: String): Future[Unit] =
    deleteByQuery(s"$key:$value")
}
