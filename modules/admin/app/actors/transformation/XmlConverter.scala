package actors.transformation

import java.time.{Duration, LocalDateTime}

import actors.transformation.XmlConverter._
import actors.transformation.XmlConverterManager.XmlConvertJob
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.Materializer
import models.UserProfile
import services.storage.{FileMeta, FileStorage}
import services.transformation.XmlTransformer

import scala.concurrent.ExecutionContext


object XmlConverter {
  // Other messages we can handle
  sealed trait Action
  case object Initial extends Action
  case object Starting extends Action
  case object Counting extends Action
  case class Counted(total: Int) extends Action
  case class Completed(total: Int, secs: Long) extends Action
  case class Error(id: String, e: Throwable) extends Action
  case class Resuming(after: String) extends Action
  case class DoneFile(id: String) extends Action
  case class FetchFiles(from: Option[String] = None) extends Action
  case class Progress(done: Int, total: Int) extends Action
  case object Status extends Action
  case class Cancelled(total: Int, secs: Long) extends Action
  case object Cancel extends Action
  case class Convert(files: List[FileMeta], truncated: Boolean, last: Option[String], count: Int)

}

case class XmlConverter (job: XmlConvertJob, transformer: XmlTransformer, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

  import akka.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case Initial =>
      val msgTo = sender()
      context.become(counting(msgTo, LocalDateTime.now()))
      msgTo ! Counting
      self ! Counting
  }

  // We're counting the full set of files to convert
  def counting(msgTo: ActorRef, start: LocalDateTime): Receive = {
    // Count files in the given prefix...
    case Counting =>
      if (job.data.only.nonEmpty) self ! Counted(1)
      else storage.count(Some(job.data.inPrefix))
          .map(filesInSrc => Counted(filesInSrc))
          .pipeTo(self)

    // Start the actual job
    case Counted(total) =>
      msgTo ! Counted(total)
      context.become(running(msgTo, 0, total, start))
      msgTo ! Starting
      self ! FetchFiles()
  }


  // The convert job is running
  def running(msgTo: ActorRef, done: Int, total: Int, start: LocalDateTime): Receive = {

    // Fetch a list of files from the storage API
    case FetchFiles(after) =>
      // If we're only converting a single key, fetch its metadata
      job.data.only.map { key =>
        storage.info(job.data.inPrefix + key)
          .map {
            case Some((meta, _)) => Convert(List(meta), truncated = false, None, done)
            case None =>
              msgTo ! Error(s"Key not found: ${job.data.inPrefix + key}",
                new RuntimeException(s"Missing key: ${job.data.inPrefix + key}"))
          }
          .pipeTo(self)
      } getOrElse {
        // Otherwise, fetch all items from the storage
        storage.listFiles(Some(job.data.inPrefix), after, max = 200)
          .map(list => Convert(list.files.toList, list.truncated, after, done))
          .pipeTo(self)
      }

    // Fetching a file
    case Convert(file :: others, truncated, _, count) =>
      context.become(running(msgTo, count, total, start))
      storage.get(file.key).map {
          case None => log.error(s"Storage.get returned None for '${file.key}': this shouldn't happen!")

          case Some((_, stream)) =>
            val newStream = stream.via(transformer.transform(job.data.transformers))
            val fileName = basename(file.key)
            storage.putBytes(
              job.data.outPrefix + fileName,
              newStream,
              contentType = Some("text/xml"),
              meta = Map("source" -> "convert")
            ).map { _ =>
              msgTo ! DoneFile(fileName)
              log.debug(s"Finished $fileName")
              Convert(others, truncated, Some(file.key), count + 1)
            }.recover { case e =>
              log.error(e, s"Error converting $fileName")
              msgTo ! Error(fileName, e)
              Convert(others, truncated, Some(file.key), count)
          }.pipeTo(self)
        }

    // Files in this batch exhausted, continue from last marker
    case Convert(Nil, true, last, count) =>
      last.foreach(from => msgTo ! Resuming(basename(from)))
      context.become(running(msgTo, count, total, start))
      self ! FetchFiles(last)

    // Files exhausted and there are no more batches, that means we're done...
    case Convert(Nil, false, _, count)  =>
      context.become(running(msgTo, count, total, start))
      msgTo ! Completed(count, time(start))

    // Status requests
    case Status =>
      msgTo ! Progress(done, total)

    // Cancel conversion
    case Cancel =>
      msgTo ! Cancelled(done, time(start))
      context.stop(self)

    case Failure(e) =>
      msgTo ! e
      context.stop(self)

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  private def time(from: LocalDateTime): Long =
    Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def basename(key: String): String =
    key.substring(key.lastIndexOf('/') + 1)
}
