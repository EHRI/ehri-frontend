package services.ingest

import models.{ContentTypes, IngestResult}
import org.apache.pekko.actor.ActorRef
import services.data.DataUser
import services.ingest.IngestService.IngestData

import scala.concurrent.Future

/**
  * No-op importer. Does nothing, nowt, nada, but on the
  * plus side has no dependencies.
  */
case class MockIngestService(res: IngestResult) extends IngestService {
  override def importData(data: IngestData) =
    Future.successful(res)

  override def remapMovedUnits(movedIds: Seq[(String, String)]) =
    Future.successful(0)

  override def reindex(scopeType: ContentTypes.Value, id: String, chan: ActorRef) =
    Future.successful(())

  override def reindex(ids: Seq[String], chan: ActorRef) =
    Future.successful(())

  override def storeManifestAndLog(jobId: String, data: IngestData, res: IngestResult) =
    Future.successful(java.net.URI.create("http://example.com/log"))

  override def clearIndex(ids: Seq[String], chan: ActorRef) =
    Future.successful(())

  override def importCoreferences(id: String, refs: Seq[(String, String)])(implicit user: DataUser) =
    Future.successful(res)

  override def emitEvents(data: IngestData, res: IngestResult): Unit = ()
}
