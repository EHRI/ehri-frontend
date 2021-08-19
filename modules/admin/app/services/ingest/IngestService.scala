package services.ingest

import akka.actor.ActorRef
import models.{ContentTypes, IngestParams, IngestResult}
import play.api.mvc.QueryStringBindable
import services.data.DataUser
import services.ingest.IngestService.IngestJob

import java.net.URI
import scala.concurrent.Future

object IngestService {

  case object IngestDataType extends Enumeration() {
    val Eac = Value("eac")
    val Ead = Value("ead")
    val EadSync = Value("ead-sync")
    val Skos = Value("skos")

    implicit val binder: QueryStringBindable[IngestDataType.Value] =
      utils.binders.queryStringBinder(this)
  }

  case class IngestData(
                         params: IngestParams,
                         dataType: IngestDataType.Value,
                         contentType: String,
                         user: DataUser,
                         instance: String,
  )

  // A job with a given ID tag
  case class IngestJob(id: String, data: IngestData)

}

trait IngestService {
  /**
    * Import data into the backend.
    *
    * @param job the job parameters
    * @return an ingest result object
    */
  def importData(job: IngestJob): Future[IngestResult]

  /**
    * Import coreference data into the backend
    *
    * @param id the scope ID
    * @param refs a sequence text-to-target-id pair
    * @return an ingest result object
    */
  def importCoreferences(id: String, refs: Seq[(String, String)])(implicit user: DataUser): Future[IngestResult]

  /**
    * Relocate items which have moved after a data sync.
    *
    * @param movedIds the old ID to new ID mapping
    * @return the number of moved items
    */
  def remapMovedUnits(movedIds: Seq[(String, String)]): Future[Int]

  /**
    * Reindex the scope in which the ingest was run.
    */
  def reindex(scopeType: ContentTypes.Value, id: String, chan: ActorRef): Future[Unit]

  /**
    * Reindex updated items.
    *
    * @param ids  a sequence of updated item IDs
    * @param chan a channel to forward progress messages
    */
  def reindex(ids: Seq[String], chan: ActorRef): Future[Unit]

  /**
    * Save a job and the result to file storage.
    *
    * @param job the ingest job
    * @param res the ingest result
    * @return an URL to the stored file
    */
  def storeManifestAndLog(job: IngestJob, res: IngestResult): Future[URI]

  /**
    * Remove IDs from the index.
    *
    * @param ids  a sequence of item IDs
    * @param chan a channel to forward progress messages
    */
  def clearIndex(ids: Seq[String], chan: ActorRef): Future[Unit]
}
