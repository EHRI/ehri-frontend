package actors.transformation

import actors.transformation.XmlConverter._
import actors.transformation.XmlConverterManager.XmlConvertJob
import akka.actor.{Actor, ActorLogging, ActorRef, Scheduler}
import akka.stream.Materializer
import models.UserProfile
import services.storage.{FileMeta, FileStorage}
import services.transformation.XmlTransformer

import java.net.URI
import java.time.{Duration, LocalDateTime}
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}


object XmlConverter {
  // Other messages we can handle
  sealed trait Action
  case class Cancelled(done: Int, fresh: Int, secs: Long) extends Action
  case class Completed(done: Int, fresh: Int, secs: Long) extends Action
  case class Convert(files: List[FileMeta], truncated: Boolean, last: Option[String], count: Int, fresh: Int) extends Action
  case class Counted(total: Int) extends Action
  case class DoneFile(id: String) extends Action
  case class Error(id: String, e: Throwable) extends Action
  case class FetchFiles(from: Option[String] = None) extends Action
  case class Progress(done: Int, total: Int) extends Action
  case class Resuming(after: String) extends Action
  case object Cancel extends Action
  case object Counting extends Action
  case object Initial extends Action
  case object Starting extends Action
  case object Status extends Action
}

case class XmlConvertError(m: String) extends Exception(m)
case class XmlConvertException(m: String) extends Exception(m)

case class XmlConverter (job: XmlConvertJob, transformer: XmlTransformer, storage: FileStorage)(
    implicit userOpt: Option[UserProfile], mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

  private val transformDigest: String = services.transformation.utils.digest(job.data.transformers)

  import akka.pattern.pipe

  override def receive: Receive = {
    // Start the initial harvest
    case Initial =>
      val msgTo = sender()
      context.become(counting(msgTo, LocalDateTime.now()))
      msgTo ! Starting
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
      context.become(running(msgTo, 0, 0, total, start))
      self ! FetchFiles()
  }


  // The convert job is running
  def running(msgTo: ActorRef, done: Int, fresh: Int, total: Int, start: LocalDateTime): Receive = {

    // Fetch a list of files from the storage API
    case FetchFiles(after) =>
      // If we're only converting a single key, fetch its metadata
      job.data.only.map { key =>
        storage.info(job.data.inPrefix + key)
          .map {
            case Some((meta, _)) => Convert(List(meta), truncated = false, None, done, fresh)
            case None => Error(key, XmlConvertException(s"Missing key: $key"))
          }
          .pipeTo(self)
      } getOrElse {
        // Otherwise, fetch all items from the storage
        storage.listFiles(Some(job.data.inPrefix), after)
          .map(list => Convert(list.files.toList, list.truncated, after, done, fresh))
          .pipeTo(self)
      }

    // Fetching a file
    case Convert(file :: others, truncated, _, count, fresh) =>
      context.become(running(msgTo, count, fresh, total, start))
      val name = basename(file.key)
      val path = job.data.outPrefix + name

      // If there's an unexpected error w/ conversion we post ourselves
      // this message to handle upstream.
      def bail = Error(file.key, XmlConvertException(
        s"File ${file.key} not found in storage: this shouldn't happen!"))

      // Sorry about this nested set of futures. What we want to do is create a fingerprint of
      // the current input file's eTag plus the conversions we're about to do. Then we check if
      // there's an output file of the right name, and whether it's got the same fingerprint. If
      // so we don't do any unnecessary work.
      val r: Future[Action] = storage.info(file.key).flatMap {
        case Some((fm, _)) =>
          val fingerPrint: String = s"${fm.eTag.getOrElse("")}:$transformDigest"
          storage.info(path).flatMap {
            // If the out file already exists and is tagged with the same conversion fingerprint
            // skip it.
            case Some((_, userMeta)) if !job.data.force
                && userMeta.contains("fingerprint")
                && userMeta.get("fingerprint").contains(fingerPrint) =>
              msgTo ! DoneFile("~ " + name)
              immediate(Convert(others, truncated, Some(file.key), count + 1, fresh))

            // No matching fingerprint found: run the conversion.
            case _ =>
              import akka.pattern.retry
              implicit val scheduler: Scheduler = context.system.scheduler

              retry(() => convertFile(file, path, fingerPrint), attempts = 2, delay = 200.millis).map { _ =>
                msgTo ! DoneFile("+ " + name)
                log.debug(s"Finished $name")
                Convert(others, truncated, Some(file.key), count + 1, fresh + 1)
              }.recover { case e =>
                log.error(e, s"Error converting $name")
                msgTo ! Error(name, e)
                Convert(others, truncated, Some(file.key), count, fresh)
              }
          }

        case None => immediate(bail)
      }

      r.pipeTo(self)

    // Files in this batch exhausted, continue from last marker
    case Convert(Nil, true, last, count, fresh) =>
      last.foreach(from => msgTo ! Resuming(basename(from)))
      context.become(running(msgTo, count, fresh, total, start))
      self ! FetchFiles(last)

    // Files exhausted and there are no more batches, that means we're done...
    case Convert(Nil, false, _, count, fresh)  =>
      context.become(running(msgTo, count, fresh, total, start))
      msgTo ! Completed(count, fresh, time(start))

    // Status requests
    case Status =>
      msgTo ! Progress(done, total)

    // Cancel conversion
    case Cancel =>
      msgTo ! Cancelled(done, fresh, time(start))

    // Handle an error from an async operation
    case Error(key, e) =>
      msgTo ! Error(key, e)

    case m =>
      log.error(s"Unexpected message: $m: ${m.getClass}")
  }

  private def convertFile(file: FileMeta, path: String, fingerPrint: String): Future[URI] =
    storage.get(file.key).flatMap {
      case Some((_, stream)) =>
          val newStream = stream.via(transformer.transform(job.data.transformers))
          storage.putBytes(
            path,
            newStream,
            contentType = Some("text/xml"),
            meta = Map("source" -> "convert", "fingerprint" -> fingerPrint)
          )

      case None => Future.failed(XmlConvertException("Unable to retrieve file from storage (this shouldn't happen!)"))
    }

  private def time(from: LocalDateTime): Long = Duration.between(from, LocalDateTime.now()).toMillis / 1000

  private def basename(key: String): String = key.replace(job.data.inPrefix, "")
}
