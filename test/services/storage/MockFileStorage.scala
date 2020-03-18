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
      fakeFiles += FileMeta(path, Instant.now(), size)
      result
    }(ec)
  }

  override def listFiles(classifier: String, prefix: Option[String]): Source[FileMeta, NotUsed] =
    Source(fakeFiles
      .filter(p => prefix.forall(p.key.startsWith)).toList)

  override def uri(classifier: String, key: String, duration: FiniteDuration = 10.minutes, contentType: Option[String]): URI =
    new URI(urlPrefix(classifier) + key)

  override def deleteFiles(classifier: String, paths: String*): Future[Seq[Boolean]] = Future {
    paths.map { path =>
      fakeFiles.find(_.key == path).map { f =>
        fakeFiles -= f
      }.isDefined
    }
  }(ec)
}
