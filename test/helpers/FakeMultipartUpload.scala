package helpers

import java.io.File

import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import play.api.test.FakeRequest

/**
 * Fake request for testing multi-part file upload controller actions.
 */
trait FakeMultipartUpload {
  /** shortcut for a request body containing a single file attachment */
  implicit class WrappedFakeRequest[A](fr: FakeRequest[A]) {
    def withFileUpload(key: String, file: File, contentType: String, data: Map[String, Seq[String]] = Map.empty): FakeRequest[AnyContentAsMultipartFormData] =
      fr.withMultipartFormDataBody(
        MultipartFormData(
          dataParts = data,
          files = Seq(FilePart[TemporaryFile](key, file.getName, Some(contentType),
            SingletonTemporaryFileCreator.create(file.toPath))),
          badParts = Seq()
        )
      )
  }
}