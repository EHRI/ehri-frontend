package services.storage

import java.io.File
import java.net.URI

import scala.concurrent.Future

trait FileStorage {

  /**
    * Put a file object in storage.
    *
    * @param classifier the "bucket", or set, to which this file will belong
    * @param path       additional file path, including the file name with extension
    * @param file       the file object to store
    * @return the file URI of the stored file
    */
  def putFile(classifier: String, path: String, file: File): Future[URI]
}
