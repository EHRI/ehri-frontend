package services.ingest

import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import services.storage.MockFileStorage

import scala.concurrent.{ExecutionContext, Future}

case class MockEadValidatorService(fileStorage: MockFileStorage) extends EadValidator {
  private implicit val mat: Materializer = fileStorage.mat
  private implicit val ec: ExecutionContext = fileStorage.mat.executionContext
  private val validator = RelaxNGEadValidator()

  override def validateEad(path: Path): Future[Seq[XmlValidationError]] = validator.validateEad(path)

  override def validateEad(url: Uri): Future[Seq[XmlValidationError]] = {
    fileStorage.fromUrl(url.toString(), "ehri-assets").flatMap {
      case Some((_, src)) => validator.validateEad(src)
      case _ => throw new RuntimeException(s"Can't get bytes for URL: $url: ${fileStorage.fakeFiles}")
    }
  }

  override def validateEad(src: Source[ByteString, _]): Future[Seq[XmlValidationError]] = validator.validateEad(src)
}
