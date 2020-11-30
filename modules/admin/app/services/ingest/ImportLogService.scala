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
  repoId: String,
  datasetId: String,
  key: String,
  versionId: Option[String]
)

@ImplementedBy(classOf[SqlImportLogService])
trait ImportLogService {
  def getHandle(unitId: String): Future[Option[ImportFileHandle]]

  def save(repoId: String, datasetId: String, job: IngestData, log: ImportLog): Future[Unit]
}
