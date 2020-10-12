package services.harvesting

import models.{HarvestEvent, UserProfile}

import scala.concurrent.Future

trait HarvestEventHandle {
  def close(): Future[Unit]

  def cancel(): Future[Unit]

  def error(t: Throwable): Future[Unit]
}

/**
  * Interface for events which record information
  * about harvesting tasks. This is a write-only
  * interface.
  */
trait HarvestEventService {
  /**
    * Get all harvesting events for the given repository
    * and job.
    *
    * @param repoId the repository ID
    * @param datasetId the dataset ID
    * @param jobId  the job ID
    * @return a sequence of harvest events
    */
  def get(repoId: String, datasetId: Option[String] = None, jobId: Option[String] = None): Future[Seq[HarvestEvent]]

  /**
    * Store a new harvest event for the given repository and job.
    *
    * @param repoId    the repository ID
    * @param datasetId the dataset ID
    * @param jobId     the job ID
    * @param info      optional unstructured text information
    * @param userOpt   the user context of this event
    * @return a handle with which to conclude the harvest job
    */
  def save(repoId: String,
    jobId: String,
    datasetId: String,
    info: Option[String] = None)(implicit userOpt: Option[UserProfile]): Future[HarvestEventHandle]
}
