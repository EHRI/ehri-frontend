package services.ingest

import akka.stream.scaladsl.Source
import com.google.inject.ImplementedBy
import models.ImportLog
import play.api.libs.json.{Json, Writes}
import services.ingest.IngestService.IngestData
import utils.db.StorableEnum

import java.time.Instant
import scala.concurrent.Future


object ImportLogOpType extends Enumeration with StorableEnum {
  val Created = Value("created")
  val Updated = Value("updated")
  val Unchanged = Value("unchanged")
}

case class ImportFileHandle(
                             eventId: String,
                             repoId: String,
                             datasetId: String,
                             key: String,
                             versionId: Option[String]
                           )

case class Snapshot(id: Int, created: Instant, notes: Option[String])

object Snapshot {
  implicit val _writes: Writes[Snapshot] = Json.format[Snapshot]
}


@ImplementedBy(classOf[SqlImportLogService])
trait ImportLogService {
  /**
    * Fetch a set of import file handles for a given unit id.
    *
    * @param unitId the ID of a database item
    * @return a most-recent-first sequence of file handles
    */
  def getHandles(unitId: String): Future[Seq[ImportFileHandle]]

  /**
    * Update file handles with a new unit ID, for when an item
    * has been renamed. This should not be happening often.
    *
    * @param mappings the old ID to new ID mapping
    * @return the number of references changed
    */
  def updateHandles(mappings: Seq[(String, String)]): Future[Int]

  /**
    * Save import job data.
    *
    * @param repoId    the repository ID
    * @param datasetId the dataset ID
    * @param job       the job input data
    * @param log       the job output data
    */
  def save(repoId: String, datasetId: String, job: IngestData, log: ImportLog): Future[Unit]


  /**
    * Create a snapshot of identifiers for a repository at a given time.
    *
    * @param repoId the repository ID
    * @param idMap  the map of global to local identifiers for all items
    *               in the specified repository
    */
  def saveSnapshot(repoId: String, idMap: Source[(String, String), _], notes: Option[String] = None): Future[Snapshot]

  /**
    * Delete a snapshot for the given repository.
    *
    * @param repoId the repository ID
    * @param id     the snapshot ID
    * @return the number of items deleted
    */
  def deleteSnapshot(repoId: String, id: Int): Future[Int]

  /**
    * Retrieve the items associated with a given snapshot.
    *
    * @param id the snapshot ID
    */
  def snapshotIdMap(id: Int): Future[Seq[(String, String)]]

  /**
    * List snapshots for a given repository.
    *
    * @param repoId the repository ID
    * @return a sequence of snapshot id, creation time and optional notes in
    *         most-recently-created order
    */
  def snapshots(repoId: String): Future[Seq[Snapshot]]

  /**
    * Return a list of item IDs that have not been touched by imports
    * since the given snapshot. This can be used to determine items that
    * have
    *
    * @param repoId     the repository ID
    * @param snapshotId the snapshot ID
    * @return a list of items that do not exist in import logs for the given repository
    *         since the given snapshot
    */
  def findUntouchedItemIds(repoId: String, snapshotId: Int): Future[Seq[(String, String)]]

  /**
    * Fetch errors for a dataset's most-recent import.
    *
    * @param repoId    the repository ID
    * @param datasetId the dataset ID
    * @return a set of key/error tuples
    */
  def errors(repoId: String, datasetId: String): Future[Seq[(String, String)]]

  def findRedirects(repoId: String, snapshotId: Int): Future[Seq[(String, String)]]
}
