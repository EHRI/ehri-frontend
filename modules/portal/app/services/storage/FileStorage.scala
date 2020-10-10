package services.storage

import java.net.URI

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

trait FileStorage {

  /**
    * Get a bytestream for the given file.
    *
    * @param classifier the "bucket", or set, to which this file will belong
    * @param path       additional file path, including the file name with extension
    * @return a file-meta/byte stream pair
    */
  def get(classifier: String, path: String): Future[Option[(FileMeta, Source[ByteString, _])]]

  /**
    * Get info about a given file.
    *
    * @param classifier the "bucket", or set, to which this file will belong
    * @param path       additional file path, including the file name with extension
    * @return a file-meta object
    */
  def info(classifier: String, path: String): Future[Option[FileMeta]]

  /**
    * Get the URI for a stored file with the given classifier and key.
    *
    * @param classifier  the "bucket", or set, to which this file will belong
    * @param path        additional file path, including the file name with extension
    * @param duration    the duration for which the URI is valid
    * @param contentType the content type of the file, if the URI is
    *                    for file upload
    * @return the file URI of the stored file
    */
  def uri(classifier: String, path: String, duration: FiniteDuration = 10.minutes, contentType: Option[String] = None): URI

  /**
    * Put a file object in storage.
    *
    * @param classifier  the "bucket", or set, to which this file will belong
    * @param path        additional file path, including the file name with extension
    * @param file        the file object to store
    * @param contentType the content/type value. If None is given this will
    *                    be inferred from the path extension, or - if that
    *                    fails - default to binary.
    * @param public      whether the URI is publicly accessible
    * @param meta        a set of arbitrary metadata key-value pairs. Keys
    *                    must be variable-safe names.
    * @return the file URI of the stored file
    */
  def putFile(classifier: String, path: String, file: java.io.File, contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI]

  /**
    * Put arbitrary bytes to file storage
    *
    * @param classifier  the "bucket", or set, to which this file will belong
    * @param path        additional file path, including the file name with extension
    * @param src         the byte source
    * @param contentType the content/type value. If None is given this will
    *                    be inferred from the path extension, or - if that
    *                    fails - default to binary.
    * @param public      whether the URI is publicly accessible
    * @param meta        a set of arbitrary metadata key-value pairs. Keys
    *                    must be variable-safe names.
    * @return the file URI of the stored file
    */
  def putBytes(classifier: String, path: String, src: Source[ByteString, _], contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI]

  /**
    * Stream all files which share this classifier.
    *
    * @param classifier the "bucket", or set, to which this file belongs
    * @param prefix     an optional path prefix
    * @return a stream of file metadata objects.
    */
  def streamFiles(classifier: String, prefix: Option[String] = None): Source[FileMeta, _]

  /**
    * List files which share this classifier.
    *
    * @param classifier the "bucket", or set, to which this file belongs
    * @param prefix     an optional path prefix
    * @param after      list files starting after this key
    * @param max        the maximum number of keys to list
    * @return a FileList object consisting of a sequence of file metadata objects
    *         and a boolean indicating if the stream is paged, in which case
    *         subsequent objects will need to be retrieved using the `after` parameter.
    */
  def listFiles(classifier: String, prefix: Option[String] = None, after: Option[String] = None, max: Int = -1): Future[FileList]

  /**
    * Delete files from storage.
    *
    * @param classifier the "bucket", or set, to which this file belongs
    * @param paths      file paths, including the file name with extension
    * @return paths which were successfully deleted
    */
  def deleteFiles(classifier: String, paths: String*): Future[Seq[String]]

  /**
    * Delete files with a given prefix from storage.
    *
    * @param classifier the "bucket", or set, to which this file belongs
    * @param prefix     the file path prefix
    * @return paths which were successfully deleted
    */
  def deleteFilesWithPrefix(classifier: String, prefix: String): Future[Seq[String]]
}
