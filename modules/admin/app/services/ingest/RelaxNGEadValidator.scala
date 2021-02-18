package services.ingest

import java.io.InputStreamReader
import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.google.common.io.Resources
import com.thaiopensource.util.PropertyMapBuilder
import com.thaiopensource.validate.prop.rng.RngProperty
import com.thaiopensource.validate.{ValidateProperty, ValidationDriver}
import javax.inject.Inject
import org.xml.sax.{ErrorHandler, InputSource, SAXParseException}
import play.api.Logger

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}


case class RelaxNGEadValidator @Inject()()(implicit mat: Materializer, exec: ExecutionContext) extends EadValidator {

  private val logger = Logger(classOf[RelaxNGEadValidator])
  //noinspection UnstableApiUsage
  private val rngUrl = Resources.getResource("ehri_ead.rng")
  private val rng: InputSource = ValidationDriver.uriOrFileInputSource(rngUrl.toString)

  override def validateEad(src: Source[ByteString, _]): Future[Seq[XmlValidationError]] = {
    val stream = new InputStreamReader(src.runWith(StreamConverters.asInputStream()))
    val is = new InputSource(stream)
    validateInputSource(is)
  }

  override def validateEad(path: Path): Future[Seq[XmlValidationError]] =
    validateInputSource(ValidationDriver.fileInputSource(path.toFile))

  override def validateEad(url: Uri): Future[Seq[XmlValidationError]] = {
    logger.debug(s"Validating URI: $url")
    validateInputSource(ValidationDriver.uriOrFileInputSource(url.toString))
  }

  private def validateInputSource(is: InputSource): Future[Seq[XmlValidationError]] = Future {
    val props = new PropertyMapBuilder()
    props.put(RngProperty.CHECK_ID_IDREF, null)
    val bs = ArrayBuffer[XmlValidationError]()
    val erh: ErrorHandler = new ErrorHandler {
      override def warning(e: SAXParseException): Unit = addError(e)

      override def error(e: SAXParseException): Unit = addError(e)

      override def fatalError(e: SAXParseException): Unit = addError(e)

      private def addError(e: SAXParseException): Unit =
        bs.append(XmlValidationError(e.getLineNumber, e.getColumnNumber, e.getMessage))
    }
    props.put(ValidateProperty.ERROR_HANDLER, erh)
    try {
      val driver = new ValidationDriver(props.toPropertyMap)
      driver.loadSchema(rng)
      driver.validate(is)
      bs.toSeq
    } catch {
      case e: SAXParseException =>
        Seq(XmlValidationError(e.getLineNumber, e.getColumnNumber, e.getLocalizedMessage))
    } finally {
      bs.clear()
    }
  }(exec)
}
