package services.datasets

import models.{ImportDataset, ImportDatasetInfo}

import scala.concurrent.Future

trait ImportDatasetService {

  def get(repoId: String, datasetId: String): Future[ImportDataset]

  def list(repoId: String): Future[Seq[ImportDataset]]

  def create(repoId: String, info: ImportDatasetInfo): Future[ImportDataset]

  def delete(repoId: String, datasetId: String): Future[Boolean]
}
