package services.transformation

import com.google.inject.ImplementedBy
import models.{DataTransformation, DataTransformationInfo}

import scala.concurrent.Future

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
  def get(id: Long): Future[DataTransformation]

  /**
    * Fetch a sequence of transformations by ID
    *
    * @param ids the sequence of transformation ids
    * @return an ordered sequence of transformations
    */
  def get(ids: Seq[Long]): Future[Seq[DataTransformation]]

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
  def update(id: Long, info: DataTransformationInfo, repoId: Option[String]): Future[DataTransformation]

  /**
    * Delete a transformation
    *
    * @param id the transformation's id
    * @return a confirmation boolean
    */
  def delete(id: Long): Future[Boolean]

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
    * @param repoId the repository ID
    * @return the transformations in application order
    */
  def getConfig(repoId: String): Future[Seq[DataTransformation]]

  /**
    * Save transformations for a given repository.
    *
    * @param repoId the repository ID
    * @param dtIds  an ordered sequence of transformation IDs
    * @return the number of transformations saved
    */
  def saveConfig(repoId: String, dtIds: Seq[Long]): Future[Int]
}
