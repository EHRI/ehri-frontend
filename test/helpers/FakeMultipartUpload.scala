package helpers

import java.io.{ByteArrayOutputStream, File}

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content._
import play.api.http._
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Codec, MultipartFormData}
import play.api.test.FakeRequest

import scala.language.implicitConversions

/**
 * Fake request for testing multi-part file upload
 * controller actions.
 *
 * Borrowed from: http://stackoverflow.com/a/24622059/285374
 */
trait FakeMultipartUpload {
  implicit def writeableOf_multiPartFormData(implicit codec: Codec): Writeable[MultipartFormData[TemporaryFile]] = {
    val builder = MultipartEntityBuilder.create().setBoundary("12345678")

    def transform(multipart: MultipartFormData[TemporaryFile]): Array[Byte] = {
      multipart.dataParts.foreach { part =>
        part._2.foreach { p2 =>
          builder.addPart(part._1, new StringBody(p2, ContentType.create("text/plain", "UTF-8")))
        }
      }
      multipart.files.foreach { file =>
        val part = new FileBody(file.ref.file, ContentType.create(file.contentType.getOrElse("application/octet-stream")), file.filename)
        builder.addPart(file.key, part)
      }

      val outputStream = new ByteArrayOutputStream
      builder.build.writeTo(outputStream)
      outputStream.toByteArray
    }

    new Writeable[MultipartFormData[TemporaryFile]](transform, Some(builder.build.getContentType.getValue))
  }

  /** shortcut for generating a MultipartFormData with one file part which more fields can be added to */
  def fileUpload(key: String, file: File, contentType: String): MultipartFormData[TemporaryFile] = {
    MultipartFormData(
      dataParts = Map(),
      files = Seq(FilePart[TemporaryFile](key, file.getName, Some(contentType), TemporaryFile(file))),
      badParts = Seq(),
      missingFileParts = Seq())
  }

  /** shortcut for a request body containing a single file attachment */
  case class WrappedFakeRequest[A](fr: FakeRequest[A]) {
    def withFileUpload(key: String, file: File, contentType: String) = {
      fr.withBody(fileUpload(key, file, contentType))
    }
  }
  implicit def toWrappedFakeRequest[A](fr: FakeRequest[A]): WrappedFakeRequest[A] = WrappedFakeRequest(fr)
}