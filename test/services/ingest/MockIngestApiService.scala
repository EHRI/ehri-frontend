package services.ingest
import akka.actor.ActorRef
import defines.ContentTypes

import scala.concurrent.Future

/**
  * No-op importer. Does nothing, nowt, nada, but on the
  * plus side has no dependencies.
  */
case class MockIngestApiService(res: IngestResult) extends IngestApi {
  override def importData(job: IngestApi.IngestJob) =
    Future.successful(res)

  override def remapMovedUnits(movedIds: Seq[(String, String)]) =
    Future.successful(0)

  override def reindex(scopeType: ContentTypes.Value, id: String, chan: ActorRef) =
    Future.successful(())

  override def reindex(ids: Seq[String], chan: ActorRef) =
    Future.successful(())

  override def storeManifestAndLog(job: IngestApi.IngestJob, res: IngestResult) =
    Future.successful(java.net.URI.create("http://example.com/log"))

  override def clearIndex(ids: Seq[String], chan: ActorRef) =
    Future.successful(())
}
