package services.storage

import java.io.File
import java.net.URI

import scala.concurrent.Future

case class MockFileStorage(fakeFiles: collection.mutable.ListBuffer[URI]) extends FileStorage {
  override def putFile(classifier: String, path: String, file: File): Future[URI] = {
    val result: URI = new URI(s"https://$classifier.mystorage.com/$path")
    fakeFiles += result
    Future.successful(result)
  }
}