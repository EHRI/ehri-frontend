package services.ingest

import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

trait EadValidator {

  def validateEad(path: Path): Future[Seq[XmlValidationError]]

  def validateEad(url: Uri): Future[Seq[XmlValidationError]]

  def validateEad(src: Source[ByteString, _]): Future[Seq[XmlValidationError]]
}
