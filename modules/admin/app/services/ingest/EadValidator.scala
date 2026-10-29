package services.ingest

import java.nio.file.Path

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

object EadSchema extends Enumeration {
  val Ead2002 = Value("ead2002")
  val Ead3 = Value("ead3")
  val Ead4 = Value("ead4")
}

trait EadValidator {

  def validateEad(path: Path, schema: EadSchema.Value): Future[Seq[XmlValidationError]]

  def validateEad(url: Uri, schema: EadSchema.Value): Future[Seq[XmlValidationError]]

  def validateEad(src: Source[ByteString, _], schema: EadSchema.Value): Future[Seq[XmlValidationError]]

  // Scala doesn't allow default arguments on more than one overload of the
  // same method, so the schema-less convenience forms are plain delegation
  // instead - implementations only need to provide the 2-arg versions above.
  final def validateEad(path: Path): Future[Seq[XmlValidationError]] = validateEad(path, EadSchema.Ead2002)

  final def validateEad(url: Uri): Future[Seq[XmlValidationError]] = validateEad(url, EadSchema.Ead2002)

  final def validateEad(src: Source[ByteString, _]): Future[Seq[XmlValidationError]] = validateEad(src, EadSchema.Ead2002)
}
