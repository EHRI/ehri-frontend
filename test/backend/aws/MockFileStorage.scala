package backend

import java.io.File
import java.net.URI

import scala.concurrent.{Future, ExecutionContext}

case class MockFileStorage() extends FileStorage {
  override def putFile(instance: String, bucket: String, path: String, file: File)(implicit executionContext: ExecutionContext): Future[URI] =
    Future.successful(new URI(s"https://$bucket.mystorage.com/$instance/$path"))
}