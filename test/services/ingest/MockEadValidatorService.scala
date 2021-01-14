package services.ingest

import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.Configuration
import services.storage.FileStorage

import java.net.URI
import java.nio.file.Path
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

case class MockEadValidatorService @Inject() (@Named("dam") storage: FileStorage, config: Configuration)(implicit map: Materializer, ec: ExecutionContext) extends EadValidator {

  private val validator = RelaxNGEadValidator()

  override def validateEad(path: Path): Future[Seq[XmlValidationError]] = validator.validateEad(path)

  override def validateEad(uri: Uri): Future[Seq[XmlValidationError]] = {
    storage.fromUri(URI.create(uri.toString())).flatMap {
      case Some((_, src)) => validator.validateEad(src)
      case _ => throw new RuntimeException(s"Can't get bytes for URL: $uri in '${storage.name}'")
    }
  }

  override def validateEad(src: Source[ByteString, _]): Future[Seq[XmlValidationError]] = validator.validateEad(src)
}
