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
import javax.inject.{Inject, Singleton}
import org.xml.sax.{ErrorHandler, InputSource, SAXParseException}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class EadValidatorService @Inject() ()(implicit mat: Materializer, exec: ExecutionContext) extends EadValidator {

  private val rngUrl = Resources.getResource("ehri_ead.rng")
  private val rng: InputSource = ValidationDriver.uriOrFileInputSource(rngUrl.toString)
  private val bs = ArrayBuffer[Error]()
  private val erh: ErrorHandler = new ErrorHandler {
    override def warning(e: SAXParseException): Unit = addError(e)
    override def error(e: SAXParseException): Unit = addError(e)
    override def fatalError(e: SAXParseException): Unit = addError(e)

    private def addError(e: SAXParseException): Unit =
      bs.append(Error(e.getLineNumber, e.getColumnNumber, e.getMessage))
  }
  private val props = new PropertyMapBuilder()
  props.put(ValidateProperty.ERROR_HANDLER, erh)
  props.put(RngProperty.CHECK_ID_IDREF, null)
  private val driver = new ValidationDriver(props.toPropertyMap)
  driver.loadSchema(rng)

  override def validateEad(src: Source[ByteString, _]): Future[Seq[Error]] = {
    val stream = new InputStreamReader(src.runWith(StreamConverters.asInputStream()))
    val is = new InputSource(stream)
    validateInputSource(is)
  }

  override def validateEad(path: Path): Future[Seq[Error]] =
    validateInputSource(ValidationDriver.fileInputSource(path.toFile))

  override def validateEad(url: Uri): Future[Seq[Error]] =
    validateInputSource(ValidationDriver.uriOrFileInputSource(url.toString))

  private def validateInputSource(is: InputSource): Future[Seq[Error]] = Future {
    try {
      driver.validate(is)
      Seq(bs: _*)
    } catch {
      case e: SAXParseException =>
        Seq(Error(e.getLineNumber, e.getColumnNumber, e.getLocalizedMessage))
    } finally {
      bs.clear()
    }
  }(exec)
}
