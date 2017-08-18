package services.ingest

import akka.actor.ActorRef
import services.data.ApiUser
import services.ingest.IngestApi.IngestJob

import scala.concurrent.Future

object IngestApi {

  case class IngestData(
    dataType: String,
    params: IngestParams,
    contentType: String,
    file: java.io.File,
    user: ApiUser
  )

  // A job with a given ID tag
  case class IngestJob(id: String, data: IngestData)
}

trait IngestApi {
  /**
    * Run the index job, with progress messages forwarded
    * to the given actor.
    *
    * @param job  the job parameters
    * @param chan a channel to post progress messages to
    */
  def run(job: IngestJob, chan: ActorRef): Future[Unit]
}