package services.harvesting

import akka.actor.ActorRef
import akka.stream.Materializer
import javax.inject.Inject
import play.api.http.MimeTypes
import play.api.i18n.Messages
import play.api.libs.json.Json
import services.ingest.OaiPmhClient
import services.storage.FileStorage

import scala.concurrent.{ExecutionContext, Future}


case class OaiPmhHarvestApiService @Inject()(oaiPmhClient: OaiPmhClient)(implicit ec: ExecutionContext, mat: Materializer)
extends OaiPmhHarvestApi {

  private val logger = play.api.Logger(classOf[OaiPmhHarvestApiService])

  import services.harvesting.OaiPmhHarvestApi._

  private def msg(s: String, chan: Seq[ActorRef]): Unit = {
    logger.info(s)
    chan.foreach(_ ! s)
  }

  private def fileName(prefix: String, id: String): String = prefix + id + ".xml"

  /**
    * Harvest an OAI-PMH endpoint to the given storage location.
    *
    * @param job         the job parameters
    * @param storage the file system on which to store harvested data
    * @param chan        channels for receiving progress messages
    * @return a future containing the number of files harvested
    */
  def run(job: OaiPmhHarvestJob, storage: FileStorage, chan: Seq[ActorRef] = Seq.empty): Future[Int] = {
    oaiPmhClient
      .listIdentifiers(job.config)
      .mapAsync(1)(id => {
        storage.putBytes(
          job.bucket,
          fileName(job.prefix, id),
          oaiPmhClient.getRecord(job.config, id),
          Some(MimeTypes.XML)
        ).map (uri => id -> uri)
      })
      .map { case (id, uri) =>
        msg(s"$id -> $uri", chan)
        uri
      }
      .runFold(0) { case (i, _) => i + 1 }
  }
}
