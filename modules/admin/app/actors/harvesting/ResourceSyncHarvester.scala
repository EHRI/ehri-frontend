package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting.Harvester.HarvestJob
import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef}
import org.apache.pekko.http.scaladsl.model.Uri
import models.{FileLink, ResourceSyncConfig, UserProfile}
import services.harvesting.ResourceSyncClient
import services.storage.FileStorage

import java.time.{Duration, Instant}
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


object ResourceSyncHarvester {

  // Internal message we send ourselves
  sealed trait ResourceSyncAction
  private case class Fetch(ids: List[FileLink], count: Int, fresh: Int) extends ResourceSyncAction

  /**
    * A description of an OAI-ResourceSync harvest task.
    *
    * @param config     the endpoint configuration
    * @param prefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class ResourceSyncData(
    config: ResourceSyncConfig,
    prefix: String,
  )

  /**
    * A single harvest job with a unique ID.
    */
  case class ResourceSyncJob(repoId: String, datasetId: String, jobId: String, data: ResourceSyncData)
    extends HarvestJob
}


case class ResourceSyncHarvester (client: ResourceSyncClient, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import Harvester._
  import ResourceSyncHarvester._
  import org.apache.pekko.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case job: ResourceSyncJob =>
      val msgTo = sender()
      context.become(running(job, msgTo, 0, 0, Instant.now()))
      msgTo ! Starting
      client.list(job.data.config)
        .map {list =>
          msgTo ! ToDo(list.size)
          Fetch(list.toList, 0, 0)
        }
        .pipeTo(self)
  }


  // The harvest is running
  def running(job: ResourceSyncJob, msgTo: ActorRef, done: Int, fresh: Int, start: Instant): Receive = {
    // Harvest an individual item
    case Fetch(item :: rest, count, fresh) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(job, msgTo, count, fresh, start))

      copyItem(job, item).map { case (name, isFresh) =>
        msgTo ! DoneFile(name)
        Fetch(rest, count + 1, if (isFresh) fresh + 1 else fresh)
      }.pipeTo(self)

    // Finished harvesting this resource list
    case Fetch(Nil, done, fresh) =>
      msgTo ! Completed(done, fresh, time(start))

    // Cancel harvest
    case Cancel =>
      msgTo ! Cancelled(done, fresh, time(start))

    case Failure(e) =>
      msgTo ! e

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  private def copyItem(job: ResourceSyncJob, item: FileLink): Future[(String, Boolean)] = {
    // Strip the hostname from the file URL but use the
    // rest of the path
    val name = Uri(item.loc).path.dropChars(1)
    val path = job.data.prefix + name

    // file metadata
    val meta = Map(
      "source" -> "rs",
      "rs-endpoint" -> job.data.config.url,
      "rs-filter" -> job.data.config.filter.getOrElse(""),
      "rs-job-id" -> job.jobId,
    ) ++ item.hash.map(h => "hash" -> h)

    log.debug(s"Item: $meta")
    // Get the storage metadata for checking the file hash...
    storage.info(path).flatMap {
      // If it exists and matches we've got nowt to do..
      case Some((_, userMeta)) if userMeta.contains("hash") && userMeta.get("hash") == item.hash =>
        immediate(("~ " + name, false))

      // Either the hash doesn't match or the file's not there yet
      // so upload it now...
      case _ =>
        val bytes = client.get(job.data.config, item)
        storage.putBytes(
          path,
          bytes,
          item.contentType,
          meta = meta
        ).map { _ => ("+ " + name, true) }
    }
  }

  private def time(from: Instant): Long =
    Duration.between(from, Instant.now()).toMillis / 1000
}
