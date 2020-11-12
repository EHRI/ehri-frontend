package services.storage

import java.net.URI
import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

sealed trait FileMarker
case object Deleted extends FileMarker
case class Version(meta: FileMeta, data: ByteString) extends FileMarker


case class MockFileStorage(fakeFiles: collection.mutable.Map[String, Map[String, Seq[FileMarker]]]) extends FileStorage {

  implicit val as: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer = Materializer(as)
  private implicit val ec: ExecutionContext = mat.executionContext

  private def urlPrefix(classifier: String): String = s"https://$classifier.mystorage.com/"

  private def bucket(classifier: String): Map[String, Seq[FileMarker]] =
    fakeFiles.getOrElse(classifier, Map.empty)

  private def fileMeta(classifier: String): immutable.Seq[FileMeta] = bucket(classifier)
    .values
    .map(_.headOption)
    .collect { case Some(Version(m, _)) => m}
    .toList

  private def getF(classifier: String, path: String): Option[FileMarker] =
    bucket(classifier).get(path).flatMap(_.headOption)

  private def put(classifier: String, path: String, bytes: ByteString, contentType: Option[String]): Unit = {
    val existing: Seq[FileMarker] = bucket(classifier).getOrElse(path, Seq.empty)
    val newVersion = Version(FileMeta(classifier, path, Instant.now, bytes.size, Some(bytes.hashCode.toString), contentType), bytes)
    val versions: Seq[FileMarker] = newVersion +: existing
    fakeFiles += (classifier -> (bucket(classifier) + (path -> versions)))
  }

  private def del(classifier: String, path: String): Unit = {
    val newVersions = Deleted +: bucket(classifier)(path).tail
    fakeFiles += (classifier -> (bucket(classifier) + (path -> newVersions)))
  }

  override def info(classifier: String, path: String): Future[Option[FileMeta]] = Future {
    fileMeta(classifier).find(_.key == path)
  }(ec)

  override def get(classifier: String, path: String): Future[Option[(FileMeta, Source[ByteString, _])]] = Future {
    getF(classifier, path).flatMap {
      case Version(m, bytes) => Some(m -> Source.single(bytes))
      case Deleted => None
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
      .values
      .map(_.headOption)
      .collect { case Some(Version(m, _)) => m}
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

  override def deleteFilesWithPrefix(classifier: String, prefix: String): Future[Seq[String]] = Future {
    fileMeta(classifier).filter(_.key.startsWith(prefix)).map { f =>
      del(classifier, f.key)
      f.key
    }
  }(ec)

  override def count(classifier: String, prefix: Option[String]): Future[Int] = Future {
    fileMeta(classifier).count(f => prefix.isEmpty || prefix.forall(f.key.startsWith))
  }(ec)

  /**
    * A public method for testing purposes only.
    */
  def fromUrl(url: String, classifier: String): Future[Option[(FileMeta, Source[ByteString, _])]] =
    if (url.startsWith(urlPrefix(classifier)))
      get(classifier, url.replace(urlPrefix(classifier), ""))
    else Future.successful(Option.empty)

  override def enableVersioning(classifier: String) = Future.successful(())
}
