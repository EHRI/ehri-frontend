package services.ingest

import java.io.ByteArrayOutputStream
import java.nio.file.Path

import com.google.common.io.Resources
import com.thaiopensource.util.PropertyMapBuilder
import com.thaiopensource.validate.auto.AutoSchemaReader
import com.thaiopensource.validate.prop.rng.RngProperty
import com.thaiopensource.validate.{ValidateProperty, ValidationDriver}
import com.thaiopensource.xml.sax.ErrorHandlerImpl
import javax.inject.{Inject, Singleton}
import org.xml.sax.InputSource
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.xml.SAXParseException

@Singleton
case class EadValidatorService @Inject() ()(implicit exec: ExecutionContext) extends EadValidator {

  private val logger = Logger(classOf[EadValidatorService])

  private val sr = new AutoSchemaReader()
  private val rngUrl = Resources.getResource("ehri_ead.rng")
  private val rng: InputSource = ValidationDriver.uriOrFileInputSource(rngUrl.toString)
  private val bs = new ByteArrayOutputStream()
  private val erh = new ErrorHandlerImpl(bs)
  private val props = new PropertyMapBuilder()
  props.put(ValidateProperty.ERROR_HANDLER, erh)
  props.put(RngProperty.CHECK_ID_IDREF, null)
  private val driver = new ValidationDriver(props.toPropertyMap, sr)
  driver.loadSchema(rng)

  private val re: Regex = ".+:(\\d+):(\\d+): error: (.+)".r

  override def validateEad(path: Path): Future[Seq[Error]] = Future {
    try {
      val is: InputSource = ValidationDriver.fileInputSource(path.toFile)
      driver.validate(is)
      val errs = bs.toString.split('\n').toSeq.collect {
        case re(line, pos, err) => Error(line.toInt, pos.toInt, err)
      }
      errs
    } catch {
      case e: SAXParseException =>
        Seq(Error(e.getLineNumber, e.getColumnNumber, e.getLocalizedMessage))
    } finally {
      bs.reset()
    }
  }(exec)
}
