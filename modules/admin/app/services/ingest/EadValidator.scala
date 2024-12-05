package services.ingest

import java.nio.file.Path

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

trait EadValidator {

  def validateEad(path: Path): Future[Seq[XmlValidationError]]

  def validateEad(url: Uri): Future[Seq[XmlValidationError]]

  def validateEad(src: Source[ByteString, _]): Future[Seq[XmlValidationError]]
}
