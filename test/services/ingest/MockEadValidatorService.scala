package services.ingest

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Configuration
import services.storage.FileStorage

import java.net.URI
import java.nio.file.Path
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

case class MockEadValidatorService @Inject() (@Named("dam") storage: FileStorage, config: Configuration)(implicit map: Materializer, ec: ExecutionContext) extends EadValidator {

  private val validator = RelaxNGEadValidator()

  override def validateEad(path: Path, schema: EadSchema.Value): Future[Seq[XmlValidationError]] = validator.validateEad(path, schema)

  override def validateEad(uri: Uri, schema: EadSchema.Value): Future[Seq[XmlValidationError]] = {
    storage.fromUri(URI.create(uri.toString())).flatMap {
      case Some((_, src)) => validator.validateEad(src, schema)
      case _ => throw new RuntimeException(s"Can't get bytes for URL: $uri in '${storage.name}'")
    }
  }

  override def validateEad(src: Source[ByteString, _], schema: EadSchema.Value): Future[Seq[XmlValidationError]] = validator.validateEad(src, schema)
}
