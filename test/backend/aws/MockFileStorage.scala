package backend.aws

import java.io.File
import java.net.URI

import backend.FileStorage

import scala.concurrent.{Future, ExecutionContext}

case class MockFileStorage(fakeFiles: collection.mutable.ListBuffer[URI]) extends FileStorage {
  override def putFile(instance: String, classifier: String, path: String, file: File)(implicit executionContext: ExecutionContext): Future[URI] = {
    val result: URI = new URI(s"https://$classifier.mystorage.com/$instance/$path")
    fakeFiles += result
    Future.successful(result)
  }
}