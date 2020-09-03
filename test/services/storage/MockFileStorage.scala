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


case class MockFileStorage(fakeFiles: collection.mutable.Map[String, Map[String, (FileMeta, ByteString)]]) extends FileStorage {

  private implicit val as: ActorSystem = ActorSystem("test")
  private implicit val mat: Materializer = Materializer(as)
  private implicit val ec: ExecutionContext = mat.executionContext
  private def urlPrefix(classifier: String) = s"https://$classifier.mystorage.com/"

  private def bucket(classifier: String): Map[String, (FileMeta, ByteString)] =
    fakeFiles.getOrElse(classifier, Map.empty)

  private def fileMeta(classifier: String) = bucket(classifier).values.map(_._1).toList

  private def getF(classifier: String, path: String): Option[(FileMeta, ByteString)] =
    bucket(classifier).get(path)

  private def put(classifier: String, path: String, bytes: ByteString, contentType: Option[String]): Unit = {
    val payload = (FileMeta(classifier, path, Instant.now, bytes.size, Some(bytes.hashCode.toString), contentType), bytes)
    fakeFiles += (classifier -> (bucket(classifier) + (path -> payload)))
  }

  private def del(classifier: String, path: String): Unit = {
    fakeFiles += (classifier -> (bucket(classifier) - path))
  }

  override def info(classifier: String, path: String): Future[Option[FileMeta]] = Future {
    fileMeta(classifier).find(_.key == path)
  }(ec)

  override def get(classifier: String, path: String): Future[Option[(FileMeta, Source[ByteString, _])]] = Future {
    getF(classifier, path).map {
      case (m, bytes) => (m, Source.single(bytes))
    }
  }(ec)

  override def putFile(classifier: String, path: String, file: java.io.File, contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] = {
    putBytes(classifier, path, FileIO.fromPath(file.toPath), contentType, public)
  }

  override def putBytes(classifier: String, path: String, src: Source[ByteString, _], contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] = {
    src.runFold(ByteString.empty)(_ ++ _).map { bytes =>
      val result: URI = new URI(urlPrefix(classifier) + path)
      put(classifier, path, bytes, contentType)
      result
    }(ec)
  }

  override def streamFiles(classifier: String, prefix: Option[String]): Source[FileMeta, NotUsed] =
    Source(fakeFiles.getOrElse(classifier, Map.empty)
      .values.map(_._1)
      .filter(p => prefix.isEmpty || prefix.forall(p.key.startsWith)).toList)

  override def listFiles(classifier: String, prefix: Option[String] = None, after: Option[String] = None, max: Int = -1): Future[FileList] = Future {
    val all = fileMeta(classifier).filter(p => prefix.isEmpty || prefix.forall(p.key.startsWith))
    val idx = after.map(a => fileMeta(classifier).indexWhere(_.key == a)).getOrElse(-1) + 1
    if (max >= 0) FileList(all.slice(idx, idx + max), (idx + max) < all.size)
    else FileList(all, truncated = false)
  }(ec)

  override def uri(classifier: String, key: String, duration: FiniteDuration = 10.minutes, contentType: Option[String]): URI =
    new URI(urlPrefix(classifier) + key)

  override def deleteFiles(classifier: String, paths: String*): Future[Seq[String]] = Future {
    paths.flatMap { path =>
      fileMeta(classifier).find(_.key == path).map { f =>
        del(classifier, path)
        f.key
      }.toSeq
    }
  }(ec)
}
