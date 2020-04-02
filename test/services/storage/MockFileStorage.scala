package services.storage

import java.net.URI
import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


case class MockFileStorage(fakeFiles: collection.mutable.ListBuffer[FileMeta]) extends FileStorage {

  private implicit val as: ActorSystem = ActorSystem("test")
  private implicit val mat: Materializer = Materializer(as)
  private implicit val ec: ExecutionContext = mat.executionContext
  private def urlPrefix(classifier: String) = s"https://$classifier.mystorage.com/"

  override def putFile(classifier: String, path: String, file: java.io.File, public: Boolean = false): Future[URI] = {
    putBytes(classifier, path, FileIO.fromPath(file.toPath))
  }

  override def putBytes(classifier: String, path: String, src: Source[ByteString, _], public: Boolean = false): Future[URI] = {
    src.runFold(0)((b, s) => b + s.size).map { size =>
      val result: URI = new URI(urlPrefix(classifier) + path)
      fakeFiles += FileMeta(classifier, path, Instant.now(), size, (classifier + path).hashCode.toString)
      result
    }(ec)
  }

  override def streamFiles(classifier: String, prefix: Option[String]): Source[FileMeta, NotUsed] =
    Source(fakeFiles.filter(p => prefix.isEmpty || prefix.forall(p.key.startsWith)).toList)

  override def listFiles(classifier: String, prefix: Option[String], after: Option[String] = None, max: Int = -1): Future[FileList] = Future {
    val all = fakeFiles.filter(p => prefix.isEmpty || prefix.forall(p.key.startsWith))
    val idx = after.map(a => fakeFiles.indexWhere(_.key == a)).getOrElse(-1)
    FileList(all.slice(idx + 1, idx + 1 + max).toList, (idx + 1 + max) <= all.size)
  }(ec)

  override def uri(classifier: String, key: String, duration: FiniteDuration = 10.minutes, contentType: Option[String]): URI =
    new URI(urlPrefix(classifier) + key)

  override def deleteFiles(classifier: String, paths: String*): Future[Seq[String]] = Future {
    paths.flatMap { path =>
      fakeFiles.find(_.key == path).map { f =>
        fakeFiles -= f
        f.key
      }.toSeq
    }
  }(ec)
}
