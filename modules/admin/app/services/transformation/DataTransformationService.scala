package services.transformation

import akka.util.ByteString
import com.google.inject.ImplementedBy
import models.{DataTransformation, DataTransformationInfo}
import play.api.http.{MimeTypes, Writeable}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

case class DataTransformationExists(name: String, cause: Throwable)
  extends Exception(s"A transformation with that name already exists: '$name'", cause)

object DataTransformationExists {
  implicit val writeableOf_json: Writeable[DataTransformationExists] =
    new Writeable(e =>
      ByteString.fromString(
        Json.stringify(Json.obj("error" -> e.getMessage, "field" -> "name"))), Some(MimeTypes.JSON))
}

/**
  * Data access object trait for managing data transformations.
  */
@ImplementedBy(classOf[SqlDataTransformationService])
trait DataTransformationService {
  /**
    * List available data transformations
    *
    * @return a sequence of transformations
    */
  def list(): Future[Seq[DataTransformation]]

  /**
    * Fetch a transformation by id
    *
    * @param id the transformation's id
    * @return a transformation object
    */
  def get(id: String): Future[DataTransformation]

  /**
    * Fetch a sequence of transformations by ID
    *
    * @param ids the sequence of transformation ids
    * @return an ordered sequence of transformations
    */
  def get(ids: Seq[String]): Future[Seq[DataTransformation]]

  /**
    * Create a new transformation object.
    *
    * @param info   the transformation info
    * @param repoId an optional associated repository ID
    * @return the new transformation object
    */
  def create(info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation]

  /**
    * Update a transformation object
    *
    * @param id     the transformation's id
    * @param info   the transformation info
    * @param repoId an optional associated repository ID
    * @return the updated transformation
    */
  def update(id: String, info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation]

  /**
    * Delete a transformation
    *
    * @param id the transformation's id
    * @return a confirmation boolean
    */
  def delete(id: String): Future[Boolean]

  /**
    * Check a transformation is valid.
    *
    * @param info the transformation info
    * @return a confirmation boolean
    */
  def check(info: DataTransformationInfo): Future[Boolean]

  /**
    * Get a list of data transformations for a given repository.
    *
    * @param repoId    the repository ID
    * @param datasetId the dataset ID
    * @return the transformations in application order, along with their parameters
    */
  def getConfig(repoId: String, datasetId: String): Future[Seq[(DataTransformation, JsObject)]]

  /**
    * Save transformations for a given repository.
    *
    * @param repoId    the repository ID
    * @param datasetId the dataset ID
    * @param dtIds     an ordered sequence of transformation IDs
    * @return the number of transformations saved
    */
  def saveConfig(repoId: String, datasetId: String, dtIds: Seq[(String, JsObject)]): Future[Int]
}
