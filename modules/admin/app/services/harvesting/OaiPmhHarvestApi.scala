package services.harvesting

import java.time.LocalDateTime

import akka.actor.ActorRef
import com.google.inject.ImplementedBy
import models.admin.OaiPmhConfig
import services.storage.FileStorage

import scala.concurrent.Future

object OaiPmhHarvestApi {

  /**
    * A description of an OAI-PMH harvest job.
    *
    * @param config  the endpoint configuration
    * @param from    the starting date and time
    * @param to      the ending date and time
    * @param setSpec the set specification
    * @param bucket  the bucket on which to save files
    * @param prefix  the path prefix on which to save files
    */
  case class OaiPmhHarvestJob(
    config: OaiPmhConfig,
    bucket: String,
    prefix: String,
    from: Option[LocalDateTime] = None,
    to: Option[LocalDateTime] = None,
    setSpec: Option[String] = None
  )
}

@ImplementedBy(classOf[OaiPmhHarvestApiService])
trait OaiPmhHarvestApi {

  import services.harvesting.OaiPmhHarvestApi._

  /**
    * Harvest an OAI-PMH endpoint to the given storage location.
    *
    * @param job     the job parameters
    * @param storage the file system on which to store harvested data
    * @param chan    a channel to post progress messages to
    * @return a future containing the number of files harvested
    */
  def run(job: OaiPmhHarvestJob, storage: FileStorage, chan: Seq[ActorRef] = Seq.empty): Future[Int]
}
