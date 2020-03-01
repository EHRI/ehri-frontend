package services.storage

import java.net.URI
import java.time.Instant

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

trait FileStorage {

  case class File(
    key: String,
    lastModifed: Instant,
    size: Long
  )

  /**
    * Get the URI for a stored file with the given classifier and key.
    *
    * @param classifier the "bucket", or set, to which this file will belong
    * @param path       additional file path, including the file name with extension
    * @param duration   the duration for which the URI is valid
    * @return the file URI of the stored file
    */
  def uri(classifier: String, path: String, duration: FiniteDuration = 10.minutes): URI

  /**
    * Put a file object in storage.
    *
    * @param classifier the "bucket", or set, to which this file will belong
    * @param path       additional file path, including the file name with extension
    * @param file       the file object to store
    * @param public     whether the URI is publicly accessible
    * @return the file URI of the stored file
    */
  def putFile(classifier: String, path: String, file: java.io.File, public: Boolean = false): Future[URI]

  /**
    * Put arbitrary bytes to file storage
    *
    * @param classifier the "bucket", or set, to which this file will belong
    * @param path       additional file path, including the file name with extension
    * @param src        the byte source
    * @param public     whether the URI is publicly accessible
    * @return the file URI of the stored file
    */
  def putBytes(classifier: String, path: String, src: Source[ByteString, _], public: Boolean = false): Future[URI]

  /**
    * List files which share this classifier.
    *
    * @param classifier the "bucket", or set, to which this file belongs
    * @param prefix     an option path prefix
    * @return a stream of file paths
    */
  def listFiles(classifier: String, prefix: Option[String] = None): Source[FileStorage#File, _]
}
