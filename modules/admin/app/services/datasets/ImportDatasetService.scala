package services.datasets

import akka.util.ByteString
import com.google.inject.ImplementedBy
import models.{ImportDataset, ImportDatasetInfo}
import play.api.http.{MimeTypes, Writeable}
import play.api.libs.json.Json

import scala.concurrent.Future

case class ImportDatasetExists(id: String, cause: Throwable)
  extends Exception(s"A dataset with that id already exists: '$id'", cause)

object ImportDatasetExists {
  implicit val writeableOf_json: Writeable[ImportDatasetExists] =
    new Writeable(e =>
      ByteString.fromString(
        Json.stringify(Json.obj("error" -> e.getMessage, "field" -> "id"))), Some(MimeTypes.JSON))
}


@ImplementedBy(classOf[SqlImportDatasetService])
trait ImportDatasetService {

  def listAll(): Future[Map[String, Seq[ImportDataset]]]

  def get(repoId: String, datasetId: String): Future[ImportDataset]

  def list(repoId: String): Future[Seq[ImportDataset]]

  def create(repoId: String, info: ImportDatasetInfo): Future[ImportDataset]

  def update(repoId: String, datasetId: String, info: ImportDatasetInfo): Future[ImportDataset]

  def delete(repoId: String, datasetId: String): Future[Boolean]
}
