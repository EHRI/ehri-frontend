package services.ingest

import com.google.inject.ImplementedBy
import services.ingest.IngestService.IngestData
import utils.db.StorableEnum

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
}
