package actors.harvesting

import actors.LongRunningJob.Cancel
import actors.harvesting.Harvester.HarvestJob
import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import models.{BasicAuthConfig, UrlNameMap, UrlSetConfig, UserProfile}
import play.api.http.HeaderNames
import play.api.libs.ws.{WSAuthScheme, WSClient}
import services.storage.FileStorage

import java.time.{Duration, Instant}
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


object UrlSetHarvester {

  // Internal message we send ourselves
  sealed trait UrlSetAction
  private case class Fetch(urls: List[UrlNameMap], count: Int, fresh: Int) extends UrlSetAction

  /**
    * A description of an URL set harvest task.
    *
    * @param config     the endpoint configuration
    * @param prefix     the path prefix on which to save files, after
    *                   which the item identifier will be appended
    */
  case class UrlSetHarvesterData(
    config: UrlSetConfig,
    prefix: String,
  )

  /**
    * A single harvest job with a unique ID.
    */
  case class UrlSetHarvesterJob(repoId: String, datasetId: String, jobId: String, data: UrlSetHarvesterData)
    extends HarvestJob
}

case class UrlSetHarvester (client: WSClient, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import Harvester._
  import UrlSetHarvester._
  import org.apache.pekko.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case job: UrlSetHarvesterJob =>
      val msgTo = sender()
      context.become(running(job, msgTo, 0, 0, Instant.now()))
      msgTo ! Starting
      msgTo ! ToDo(job.data.config.urlMap.size)
      self ! Fetch(job.data.config.urls.toList, 0, 0)
  }


  // The harvest is running
  def running(job: UrlSetHarvesterJob, msgTo: ActorRef, done: Int, fresh: Int, start: Instant): Receive = {
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

  private def copyItem(job: UrlSetHarvesterJob, item: UrlNameMap): Future[(String, Boolean)] = {
    // Strip the hostname from the file URL but use the
    // rest of the path
    val name = item.name
    val path = job.data.prefix + name

    val withMethod = client.url(item.url).withMethod(job.data.config.method)
    val withHeaders = job.data.config.headers.fold(withMethod) { headers =>
        withMethod.withHttpHeaders(headers: _*)
    }
    val req = job.data.config.auth.fold(withHeaders) { case BasicAuthConfig(username, password) =>
      withHeaders.withAuth(username, password, WSAuthScheme.BASIC)
    }

    req.head().flatMap { headReq =>
      val etag: Option[String] = headReq.headerValues(HeaderNames.ETAG).headOption
      val ct: Option[String] = headReq.headerValues(HeaderNames.CONTENT_TYPE).headOption

      // file metadata
      val meta = Map(
        "source" -> "download",
        "download-endpoint" -> item.url,
        "download-job-id" -> job.jobId,
      ) ++ etag.map(tag => "hash" -> tag)

      log.debug(s"Item: $meta")

      storage.info(path).flatMap {

        // If it exists and matches we've got nowt to do..
        case Some((_, userMeta)) if userMeta.contains("hash") && userMeta.get("hash") == etag =>
          immediate(("~ " + name, false))

        // Either the hash doesn't match or the file's not there yet
        // so upload it now...
        case _ =>
          val bytes: Future[Source[ByteString, _]] = req.execute().map(r => r.bodyAsSource)
          bytes.flatMap { src =>
            storage.putBytes(
              path,
              src,
              ct,
              meta = meta
            ).map { _ => ("+ " + name, true) }
          }
      }
    }
  }

  private def time(from: Instant): Long =
    Duration.between(from, Instant.now()).toMillis / 1000
}
