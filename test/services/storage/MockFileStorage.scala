package services.storage

import java.io.File
import java.net.URI

import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.concurrent.Future


case class MockFileStorage(fakeFiles: collection.mutable.ListBuffer[URI]) extends FileStorage {
  override def putFile(classifier: String, path: String, file: File, public: Boolean = false): Future[URI] = {
    putBytes(classifier, path, FileIO.fromPath(file.toPath))
  }

  override def putBytes(classifier: String, path: String, src: Source[ByteString, _], public: Boolean = false): Future[URI] = {
    val result: URI = new URI(s"https://$classifier.mystorage.com/$path")
    fakeFiles += result
    Future.successful(result)
  }
}