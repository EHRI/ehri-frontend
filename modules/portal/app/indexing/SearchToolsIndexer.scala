package indexing

import java.util.Properties
import javax.inject.Inject

import backend.rest.Constants
import com.fasterxml.jackson.databind.JsonNode
import defines.EntityType
import eu.ehri.project.indexing.IndexHelper
import eu.ehri.project.indexing.IndexHelper.Builder
import eu.ehri.project.indexing.converter.impl.JsonConverter
import eu.ehri.project.indexing.index.Index
import eu.ehri.project.indexing.sink.impl.{CallbackSink, IndexJsonSink}
import eu.ehri.project.indexing.source.impl.WebJsonSource
import play.api.Logger
import play.api.libs.iteratee.Concurrent
import utils.search.{SearchIndexerHandle, SearchIndexer}
import collection.JavaConverters._

import scala.concurrent.{ExecutionContext, Future}

case class SearchToolsIndexer @Inject()(
    implicit index: Index,
    config: play.api.Configuration,
    executionContext: ExecutionContext)  extends SearchIndexer {
  override def handle: SearchIndexerHandle = new SearchToolsIndexerHandle()
}

/**
 * Wrapper for the EHRI search tools index helper, which
 * provides a more convenient API for keeping the search
 * engine updated. This uses the library in-process, which
 * is more convenient for deployment.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SearchToolsIndexerHandle(
  chan: Option[Concurrent.Channel[String]] = None,
  processFunc: String => String = identity[String]
)(implicit index: Index,
  config: play.api.Configuration,
  executionContext: ExecutionContext) extends SearchIndexerHandle {

  val logger  = Logger(this.getClass)

  val serviceBaseUrl: String = utils.serviceBaseUrl("ehridata", config)

  override def withChannel(channel: Concurrent.Channel[String], formatter: String => String) =
    copy(chan = Some(channel), processFunc = formatter)

  private def indexProperties(extra: Map[String,Any] = Map.empty): Properties = {
    val props = new Properties()
    props.put(Constants.STREAM_HEADER_NAME, true.toString)
    props.put(Constants.AUTH_HEADER_NAME, "admin")
    extra.foreach { case (k, v) => props.put(k, v.toString)}
    props
  }

  private def indexHelper(specs: String*): IndexHelper = {
    val builder = (new Builder)
      .addSink(new IndexJsonSink(index, new IndexJsonSink.EventHandler {
        override def handleEvent(event: Any): Unit = {
          chan.foreach(_.push(processFunc(event.toString)))
        }
      }))
      .addConverter(new JsonConverter)
      .addSink(new CallbackSink[JsonNode](new CallbackSink.Callback[JsonNode]() {
         var count = 0
         override def call(node: JsonNode): Unit = {
           count += 1
           if (count % 100 == 0) {
             chan.foreach(_.push(processFunc(s"Items processed: $count")))
           }
         }
         override def finish(): Unit = {
           val msg: String = s"Total items processed: $count"
           logger.debug(msg)
           chan.foreach(_.push(processFunc(msg)))
         }
       }))

    IndexHelper.urlsFromSpecs(serviceBaseUrl, specs: _*).asScala.foreach { url =>
      builder.addSource(new WebJsonSource(url, indexProperties()))
    }
    builder.build()
  }

  override def indexId(id: String): Future[Unit] = Future {
    logger.debug(s"Indexing: $id")
    indexHelper("@" + id).iterate()
  }

  override def indexChildren(entityType: EntityType.Value, id: String): Future[Unit] = Future {
    logger.debug(s"Index children: $entityType|$id")
    indexHelper(entityType + "|" + id).iterate()
  }

  override def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = Future {
    logger.debug(s"Index types: ${entityTypes.mkString(", ")}")
    indexHelper(entityTypes.map(_.toString): _*).iterate()
  }

  override def clearKeyValue(key: String, value: String): Future[Unit] = Future {
    logger.debug(s"Clear key value: $key -> $value")
    index.deleteByFieldValue(key, value, true)
  }

  override def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = Future {
    logger.debug(s"Clear types: ${entityTypes.mkString(", ")}")
    index.deleteTypes(entityTypes.map(_.toString).asJava, true)
  }

  override def clearAll(): Future[Unit] = Future {
    logger.debug(s"Delete all")
    index.deleteAll(true)
  }

  override def clearId(id: String): Future[Unit] = Future {
    logger.debug(s"Clear id: $id")
    index.deleteItem(id, true)
  }
}
