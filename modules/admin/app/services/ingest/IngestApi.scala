package services.ingest

import akka.actor.ActorRef
import play.api.mvc.QueryStringBindable
import services.data.ApiUser
import services.ingest.IngestApi.IngestJob

import scala.concurrent.Future

object IngestApi {

  case object IngestDataType extends Enumeration() {
    val Eac = Value("eac")
    val Ead = Value("ead")
    val EadSync = Value("ead-sync")
    val Skos = Value("skos")

    implicit val binder: QueryStringBindable[IngestDataType.Value] =
      utils.binders.queryStringBinder(this)
  }

  case class IngestData(
    params: IngestParams,
    dataType: IngestDataType.Value,
    contentType: String,
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