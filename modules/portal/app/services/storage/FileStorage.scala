package services.storage

import java.io.File
import java.net.URI

import scala.concurrent.{ExecutionContext, Future}

trait FileStorage {
  /**
   * Put a file object in storage.
   *
   * @param instance    an application instance discriminator, for example
   *                    the application URL
   * @param classifier  the "bucket", or set, to which this file will belong
   * @param path        additional file path, including the file name with extension
   * @param file        the file object to store
   * @return  the file URI of the stored file
   */
  def putFile(instance: String, classifier: String, path: String, file: File)(implicit executionContext: ExecutionContext):
  Future[URI]
}
