package services.storage

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import java.net.URI
import java.time.Instant
import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

sealed trait FileMarker {
  def meta: FileMeta
}

case class Deleted(meta: FileMeta) extends FileMarker

case class Version(meta: FileMeta, userMeta: Map[String, String], data: ByteString) extends FileMarker

/**
  * An in-memory implementation of the [[FileStorage]] class.
  *
  * @param name the name of this pool
  * @param db         a mutable map
  */
case class MockFileStorage(name: String, db: collection.mutable.Map[String, Seq[FileMarker]]) extends FileStorage {

  implicit val as: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer = Materializer(as)
  private implicit val ec: ExecutionContext = mat.executionContext

  private def urlPrefix: String = s"https://$name.mystorage.com/"

  private def fileMeta(versionId: Option[String] = None): immutable.Seq[(FileMeta, Map[String, String])] = db
    .values
    .map(versions => versionId.fold(ifEmpty = versions.size.toString -> versions.lastOption) { vid =>
      vid -> getV(versions, Some(vid))
    })
    .collect { case (id, Some(Version(m, um, _))) => (m.copy(versionId = Some(id)), um) }
    .toList

  private def getF(path: String, versionId: Option[String] = None): Option[FileMarker] =
    db.get(path).flatMap(f => getV(f, versionId))

  private def put(path: String, bytes: ByteString, contentType: Option[String], user: Map[String, String]): Unit = {
    val existing: Seq[FileMarker] = db.getOrElse(path, Seq.empty)
    val newVersion = Version(FileMeta(name, path, Instant.now, bytes.size, Some(bytes.hashCode.toString), contentType), user, bytes)
    val versions: Seq[FileMarker] = existing :+ newVersion
    db += path -> versions
  }

  private def del(path: String): Unit = {
    val old = db(path).lastOption.map(m => Deleted(m.meta))
    val newVersions = db(path).dropRight(1) ++ old.toSeq
    db += path -> newVersions
  }

  private def getV(versions: Seq[FileMarker], vid: Option[String] = None): Option[FileMarker] = {
    vid.map { id =>
      try versions.lift(id.toInt - 1)
      catch {
        case _: NumberFormatException => None
      }
    }.getOrElse(versions.lastOption)
  }

  override def info(path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Map[String, String])]] = Future {
    fileMeta(versionId).find(_._1.key == path)
  }(ec)

  override def get(path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Source[ByteString, _])]] = Future {
    getF(path, versionId).flatMap {
      case Version(m, _, bytes) => Some(m -> Source.single(bytes))
      case Deleted(_) => None
    }
  }(ec)

  override def putFile(path: String, file: java.io.File, contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] = {
    putBytes(path, FileIO.fromPath(file.toPath), contentType, public)
  }

  override def putBytes(path: String, src: Source[ByteString, _], contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI] = {
    src.runFold(ByteString.empty)(_ ++ _).map { bytes =>
      val result: URI = new URI(urlPrefix + path)
      put(path, bytes, contentType, meta)
      result
    }(ec)
  }

  override def streamFiles(prefix: Option[String]): Source[FileMeta, NotUsed] =
    Source(db.toMap.toSeq
      .sortBy(_._1)
      .map(_._2)
      .map(_.headOption)
      .collect { case Some(Version(m, _, _)) => m }
      .filter(p => prefix.isEmpty || prefix.forall(p.key.startsWith)).toList)

  override def listFiles(prefix: Option[String] = None, after: Option[String] = None, max: Int = -1): Future[FileList] = Future {
    val all = fileMeta().map(_._1).filter(p => prefix.isEmpty || prefix.forall(p.key.startsWith)).sortBy(_.key)
    val idx = after.map(a => fileMeta().map(_._1).sortBy(_.key).indexWhere(_.key == a)).getOrElse(-1) + 1
    if (max >= 0) FileList(all.slice(idx, idx + max), (idx + max) < all.size)
    else FileList(all, truncated = false)
  }(ec)

  override def uri(key: String, duration: FiniteDuration = 10.minutes, contentType: Option[String], versionId: Option[String] = None): URI =
    new URI(urlPrefix + key)

  override def deleteFiles(paths: String*): Future[Seq[String]] = Future {
    paths.flatMap { path =>
      fileMeta().find(_._1.key == path).map { f =>
        del(path)
        f._1.key
      }.toSeq
    }
  }(ec)

  override def deleteFilesWithPrefix(prefix: String): Future[Seq[String]] = Future {
    fileMeta().filter(_._1.key.startsWith(prefix)).map { f =>
      del(f._1.key)
      f._1.key
    }
  }(ec)

  override def count(prefix: Option[String]): Future[Int] = Future {
    fileMeta().count(f => prefix.isEmpty || prefix.forall(f._1.key.startsWith))
  }(ec)

  override def fromUri(uri: URI): Future[Option[(FileMeta, Source[ByteString, _])]] = {
    if (uri.toString.startsWith(urlPrefix))
      get(uri.toString.replace(urlPrefix, ""))
    else Future.successful(Option.empty)
  }

  override def listVersions(path: String, after: Option[String]): Future[FileList] = Future {
    FileList(db.getOrElse(path, Seq.empty)
      .zipWithIndex
      .map(m => m._1.meta.copy(versionId = Some((m._2 + 1).toString)))
      .reverse, truncated = false)
  }(ec)

  override def setVersioned(enabled: Boolean) = Future.successful(())

  override def isVersioned = Future.successful(true)
}
