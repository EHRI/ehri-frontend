package actors.transformation

import java.time.{Duration, LocalDateTime}

import actors.transformation.XmlConvertRunner._
import actors.transformation.XmlConverter.XmlConvertJob
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.Materializer
import defines.FileStage
import models.UserProfile
import services.storage.{FileMeta, FileStorage}
import services.transformation.XmlTransformer

import scala.concurrent.ExecutionContext


object XmlConvertRunner {
  // Other messages we can handle
  sealed trait Action
  case object Initial extends Action
  case object Starting extends Action
  case class Counting(src: List[FileStage.Value], total: Int) extends Action
  case class Counted(total: Int) extends Action
  case class Sources(src: List[FileStage.Value], after: Option[String])
  case class Completed(total: Int, secs: Long) extends Action
  case class Error(id: String, e: Throwable) extends Action
  case class Resuming(after: String) extends Action
  case class DoneFile(id: String) extends Action
  case class Progress(done: Int, total: Int) extends Action
  case object Status extends Action
  case class Cancelled(total: Int, secs: Long) extends Action
  case object Cancel extends Action
  case class Convert(src: List[FileStage.Value], files: List[FileMeta], truncated: Boolean, last: Option[String], count: Int)

}

case class XmlConvertRunner (job: XmlConvertJob, transformer: XmlTransformer, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

  import akka.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case Initial =>
      val msgTo = sender()
      context.become(counting(msgTo, LocalDateTime.now()))
      msgTo ! Counting(job.data.sources.toList, 0)
      self ! Counting(job.data.sources.toList, 0)
  }

  // We're counting the full set of files to convert
  def counting(msgTo: ActorRef, start: LocalDateTime): Receive = {
    // Count files from an individual source
    case Counting(src :: rest, acc) =>
      storage.streamFiles(job.data.classifier, Some(job.data.inPrefix(src)))
        .runFold(0)((acc, _) => acc + 1)
        .map( filesInSrc => Counting(rest, acc + filesInSrc))
        .pipeTo(self)

    // Exhausted sources to count
    case Counting(Nil, total) =>
      msgTo ! Counted(total)
      self ! Counted(total)

    // Start the actual job
    case Counted(total) =>
      context.become(running(msgTo, 0, total, start))
      msgTo ! Starting
      self ! Sources(job.data.sources.toList, None)
  }


  // The convert job is running
  def running(msgTo: ActorRef, done: Int, total: Int, start: LocalDateTime): Receive = {

    // Fetch a list of files from the storage API
    case Sources(src :: rest, after) =>
      storage.listFiles(job.data.classifier, Some(job.data.inPrefix(src)), after, max = 200)
        .map(list => Convert(src :: rest, list.files.toList, list.truncated, after, done))
        .pipeTo(self)

    // Not sources left: we've finished
    case Sources(Nil, _) =>
      msgTo ! Completed(done, time(start))

    // Fetching a file
    case Convert(src :: rest, file :: others, truncated, _, count) =>
      context.become(running(msgTo, count, total, start))
      storage.get(job.data.classifier, file.key).map {
          case None => log.error(s"Storage.get returned None " +
            s"for '${job.data.classifier}/${file.key}': this shouldn't happen!")

          case Some((_, stream)) =>
            val newStream = stream.via(transformer.transform(job.data.transformers))
            val fileName = basename(file.key)
            storage.putBytes(
              job.data.classifier,
              job.data.outPrefix + fileName,
              newStream,
              contentType = Some("text/xml"),
              meta = Map("source" -> "convert")
            ).map { _ =>
              msgTo ! DoneFile(fileName)
              Convert(src :: rest, others, truncated, Some(file.key), count + 1)
            }.recover { case e =>
              msgTo ! Error(fileName, e)
              Convert(src :: rest, others, truncated, Some(file.key), count)
          }.pipeTo(self)
        }

    // Files in this batch exhausted, continue from last marker
    case Convert(src :: rest, Nil, true, last, count) =>
      last.foreach(from => msgTo ! Resuming(basename(from)))
      context.become(running(msgTo, count, total, start))
      self ! Sources(src :: rest, last)

    // Files in source exhausted, continue from next source...
    case Convert(_ :: rest, Nil, false, _, count)  =>
      context.become(running(msgTo, count, total, start))
      self ! Sources(rest, None)

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
