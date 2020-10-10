package services.datasets

import com.google.inject.ImplementedBy
import models.{ImportDataset, ImportDatasetInfo}

import scala.concurrent.Future

@ImplementedBy(classOf[SqlImportDatasetService])
trait ImportDatasetService {

  def get(repoId: String, datasetId: String): Future[ImportDataset]

  def list(repoId: String): Future[Seq[ImportDataset]]

  def create(repoId: String, info: ImportDatasetInfo): Future[ImportDataset]

  def delete(repoId: String, datasetId: String): Future[Boolean]
}
