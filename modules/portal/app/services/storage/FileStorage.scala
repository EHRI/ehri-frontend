package services.storage

import java.net.URI
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.google.common.hash.Hashing
import com.google.common.io.Files

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}


object FileStorage {
  /**
    * Create a repeatable fingerprint for a given input file.
    * This is not intended to be secure but rather fast and straightforward.
    *
    * @param file the file to fingerprint
    * @return a fingerprint string
    */
  def fingerprint(file: java.io.File): String =
    Files.asByteSource(file).hash(Hashing.farmHashFingerprint64()).toString
}

trait FileStorage {

  /**
    * A name associated with this storage instance
    * @return a unique name
    */
  def name: String

  /**
    * Get a bytestream for the given file.
    *
    * @param path       additional file path, including the file name with extension
    * @param versionId  the version ID of the file content
    * @return a file-meta/byte stream pair
    */
  def get(path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Source[ByteString, _])]]

  /**
    * Get info about a given file.
    *
    * @param path       additional file path, including the file name with extension
    * @return a file-meta object
    */
  def info(path: String, versionId: Option[String] = None): Future[Option[(FileMeta, Map[String, String])]]

  /**
    * Get the URI for a stored file with the given key.
    *
    * @param path        additional file path, including the file name with extension
    * @param duration    the duration for which the URI is valid
    * @param contentType the content type of the file, if the URI is
    *                    for file upload
    * @param versionId   the version ID of the file content
    * @return the file URI of the stored file
    */
  def uri(path: String, duration: FiniteDuration = 10.minutes, contentType: Option[String] = None, versionId: Option[String] = None): URI

  /**
    * Put a file object in storage.
    *
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
  def putFile(path: String, file: java.io.File, contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI]

  /**
    * Put arbitrary bytes to file storage
    *
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
  def putBytes(path: String, src: Source[ByteString, _], contentType: Option[String] = None, public: Boolean = false, meta: Map[String, String] = Map.empty): Future[URI]

  /**
    * Stream all files
    *
    * @param prefix     an optional path prefix
    * @return a stream of file metadata objects.
    */
  def streamFiles(prefix: Option[String] = None): Source[FileMeta, _]

  /**
    * List files
    *
    * @param prefix     an optional path prefix
    * @param after      list files starting after this key
    * @param max        the maximum number of keys to list
    * @return a FileList object consisting of a sequence of file metadata objects
    *         and a boolean indicating if the stream is paged, in which case
    *         subsequent objects will need to be retrieved using the `after` parameter.
    */
  def listFiles(prefix: Option[String] = None, after: Option[String] = None, max: Int = 200): Future[FileList]

  /**
    * Delete files from storage.
    *
    * @param paths      file paths, including the file name with extension
    * @return paths which were successfully deleted
    */
  def deleteFiles(paths: String*): Future[Seq[String]]

  /**
    * Delete files with a given prefix from storage.
    *
    * @param prefix     the file path prefix
    * @return paths which were successfully deleted
    */
  def deleteFilesWithPrefix(prefix: String): Future[Seq[String]]

  /**
    * Count files, with an optional prefix.
    *
    * @param prefix     an optional prefix
    * @return the number of files in the set with the given prefix
    */
  def count(prefix: Option[String]): Future[Int]

  /**
    * List versions of a given file.
    *
    * @param path       the file path
    * @param after      list versions starting after this versionId
    * @return a FileList object consisting of a sequence of file metadata objects
    *         and a boolean indicating if the stream is paged, in which case
    *         subsequent objects will need to be retrieved using the `after` parameter.
    */
  def listVersions(path: String, after: Option[String] = None): Future[FileList]

  /**
    * Enable/disable versioning.
    * be available on some implementations.
    *
    * @param enabled    whether versioning should be enabled or disabled
    * @return
    */
  def setVersioned(enabled: Boolean): Future[Unit]

  /**
    * Check if the storage is versioned.
    *
    * @return whether or not files are versioned or not
    */
  def isVersioned: Future[Boolean]

  /**
    * Resolve a URI to a file instance. This may throw an [[UnsupportedOperationException]]
    * if the implementation does not support it.
    *
    * @param uri        a URI to resolve to a file
    * @return an optional pair of metadata and a byte source
    */
  def fromUri(uri: URI): Future[Option[(FileMeta, Source[ByteString, _])]]

  /**
    * Copy a file from one location to another.
    *
    * @param path   the path of the file to copy
    * @param toPath the path to copy the file to
    * @return the URI of the copied file
    */
  def copyFile(path: String, toPath: String): Future[URI]
}
