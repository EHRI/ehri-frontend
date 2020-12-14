package actors.harvesting

import java.time.{Duration, LocalDateTime}

import actors.harvesting.ResourceSyncHarvesterManager.ResourceSyncJob
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import models.{FileLink, UserProfile}
import play.api.libs.ws.WSClient
import services.harvesting.ResourceSyncClient
import services.storage.FileStorage

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}


object ResourceSyncHarvester {

  // Internal message we send ourselves
  private case class Fetch(ids: List[FileLink], prefix: String, count: Int) extends Action

  // Other messages we can handle
  sealed trait Action
  case object Initial extends Action
  case object Starting extends Action
  case class ToDo(num: Int) extends Action
  case class Completed(total: Int, secs: Long) extends Action
  case class Error(e: Throwable) extends Action
  case class DoneFile(name: String) extends Action
  case class Cancelled(total: Int, secs: Long) extends Action
  case object Cancel extends Action
}


case class ResourceSyncHarvester (job: ResourceSyncJob, ws: WSClient, client: ResourceSyncClient, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], ec: ExecutionContext) extends Actor with ActorLogging {
  import ResourceSyncHarvester._
  import akka.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case Initial =>
      val msgTo = sender()
      context.become(running(msgTo, 0, LocalDateTime.now()))
      msgTo ! Starting
      client.list(job.data.config)
        .map {list =>
          msgTo ! ToDo(list.size)
          Fetch(list.toList, commonDirPrefix(list.toList), 0)
        }
        .pipeTo(self)
  }


  // The harvest is running
  def running(msgTo: ActorRef, done: Int, start: LocalDateTime): Receive = {
    // Harvest an individual item
    case Fetch(item :: rest, prefix, count) =>
      log.debug(s"Calling become with new total: $count")
      context.become(running(msgTo, count, start))

      copyItem(item, prefix).map { name =>
        msgTo ! DoneFile(name)
        Fetch(rest, prefix, count + 1)
      }.pipeTo(self)

    // Finished harvesting this resource list
    case Fetch(Nil, _, done) =>
      msgTo ! Completed(done, time(start))

    // Cancel harvest
    case Cancel =>
      msgTo ! Cancelled(done, time(start))
      context.stop(self)

    case Failure(e) =>
      msgTo ! e
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  private def copyItem(item: FileLink, prefix: String): Future[String] = {
    // get the basename, or unique segment, for this file
    // in most cases this will be the basename, but not always
    val name = item.loc.replace(prefix, "")

    // file metadata
    val meta = Map(
      "source" -> "rs",
      "rs-endpoint" -> job.data.config.url,
      "rs-filter" -> job.data.config.filter.getOrElse(""),
      "rs-job-id" -> job.jobId,
    ) ++ item.hash.map(h => "hash" -> h)

    // Get the storage metadata for checking the file hash...
    storage.info(job.data.classifier, job.data.prefix + name).flatMap {
      // If it exists and matches we've got nowt to do..
      case Some((_, userMeta)) if userMeta.contains("hash") && userMeta.get("hash") == item.hash =>
        immediate("~ " + name)

      // Either the hash doesn't match or the file's not there yet
      // so upload it now...
      case _ =>
        ws.url(item.loc).get().flatMap { r =>
          storage.putBytes(
            job.data.classifier,
            job.data.prefix + name,
            r.bodyAsSource,
            item.contentType,
            meta = meta
          ).map { _ => "+ " + name }
        }
    }
  }

  private def commonDirPrefix(resLinks: List[FileLink]): String = resLinks match {
    case Nil => ""
    case head :: Nil => head.loc.substring(0, head.loc.lastIndexOf('/') + 1)
    case list =>
      // Pinched from Rosetta code because I'm too lazy to write this:
      // https://rosettacode.org/wiki/Longest_common_prefix#Scala
      def lcp(list: Seq[String]): String = list.foldLeft("") { (_, _) =>
        (list.min.view, list.max.view).zipped.takeWhile(v => v._1 == v._2).map(_._1).mkString
      }

      lcp(list.map(_.loc).sorted)
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000
}
