package helpers

import java.io.{ByteArrayOutputStream, File}

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content._
import play.api.http._
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Codec, MultipartFormData}
import play.api.test.FakeRequest

import scala.language.implicitConversions

/**
 * Fake request for testing multi-part file upload
 * controller actions.
 *
 * Borrowed from: http://stackoverflow.com/a/24622059/285374
 */
trait FakeMultipartUpload {
  private val boundary = "123455678"

  implicit def writeableOf_multiPartFormData(implicit codec: Codec): Writeable[AnyContentAsMultipartFormData] = {
    val builder = MultipartEntityBuilder.create().setBoundary(boundary)

    def transform(multipart: AnyContentAsMultipartFormData): akka.util.ByteString = {
      multipart.mfd.dataParts.foreach { part =>
        part._2.foreach { p2 =>
          builder.addPart(part._1, new StringBody(p2, ContentType.create("text/plain", "UTF-8")))
        }
      }
      multipart.mfd.files.foreach { file =>
        val part = new FileBody(file.ref.path.toFile, ContentType.create(file.contentType.getOrElse("application/octet-stream")), file.filename)
        builder.addPart(file.key, part)
      }

      val outputStream = new ByteArrayOutputStream
      builder.build.writeTo(outputStream)
      akka.util.ByteString.fromArray(outputStream.toByteArray)
    }

    new Writeable[AnyContentAsMultipartFormData](transform, Some(builder.build.getContentType.getValue))
  }

  /** shortcut for generating a MultipartFormData with one file part which more fields can be added to */
  def fileUpload(key: String, file: File, contentType: String, data: Map[String, Seq[String]] = Map.empty):
  MultipartFormData[TemporaryFile] = {
    MultipartFormData(
      dataParts = data,
      files = Seq(FilePart[TemporaryFile](key, file.getName, Some(contentType),
        // FIXME: 2.6 - rework to use injected TemporaryFileCreator
        SingletonTemporaryFileCreator.create(file.toPath))),
      badParts = Seq()
    )
  }

  /** shortcut for a request body containing a single file attachment */
  case class WrappedFakeRequest[A](fr: FakeRequest[A]) {
    def withFileUpload(key: String, file: File, contentType: String,
      data: Map[String, Seq[String]] = Map.empty): FakeRequest[AnyContentAsMultipartFormData] = {
      val mfd = fileUpload(key, file, contentType, data)
      fr.withMultipartFormDataBody(mfd).withHeaders(
        HeaderNames.CONTENT_TYPE -> s"multipart/form-data; boundary=$boundary"
      )
    }
  }
  implicit def toWrappedFakeRequest[A](fr: FakeRequest[A]): WrappedFakeRequest[A] = WrappedFakeRequest(fr)
}