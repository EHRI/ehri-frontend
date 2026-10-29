package services.ingest

import com.google.common.io.Resources
import com.thaiopensource.util.PropertyMapBuilder
import com.thaiopensource.validate.prop.rng.RngProperty
import com.thaiopensource.validate.{ValidateProperty, ValidationDriver}
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.apache.pekko.util.ByteString
import org.xml.sax.{EntityResolver, ErrorHandler, InputSource, SAXParseException}
import play.api.Logger

import java.io.{InputStreamReader, StringReader}
import java.nio.file.Path
import javax.inject.Inject
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}


case class RelaxNGEadValidator @Inject()()(implicit mat: Materializer, exec: ExecutionContext) extends EadValidator {

  private val logger = Logger(classOf[RelaxNGEadValidator])

  private val schemaResource: Map[EadSchema.Value, String] = Map(
    EadSchema.Ead2002 -> "ehri_ead.rng",
    EadSchema.Ead3 -> "ehri_ead3_undeprecated.rng",
    EadSchema.Ead4 -> "ehri_ead4.rng",
  )

  //noinspection UnstableApiUsage
  private def schemaSource(schema: EadSchema.Value): InputSource = {
    val rngUrl = Resources.getResource(schemaResource(schema))
    ValidationDriver.uriOrFileInputSource(rngUrl.toString)
  }

  override def validateEad(src: Source[ByteString, _], schema: EadSchema.Value): Future[Seq[XmlValidationError]] = {
    val inputStream = src.runWith(StreamConverters.asInputStream())
    val stream = new InputStreamReader(inputStream)
    val is = new InputSource(stream)
    validateInputSource(is, schema)
  }

  override def validateEad(path: Path, schema: EadSchema.Value): Future[Seq[XmlValidationError]] =
    validateInputSource(ValidationDriver.fileInputSource(path.toFile), schema)

  override def validateEad(url: Uri, schema: EadSchema.Value): Future[Seq[XmlValidationError]] = {
    logger.debug(s"Validating URI: $url")
    validateInputSource(ValidationDriver.uriOrFileInputSource(url.toString), schema)
  }

  private def validateInputSource(is: InputSource, schema: EadSchema.Value): Future[Seq[XmlValidationError]] = Future {
    val props = new PropertyMapBuilder()
    props.put(RngProperty.CHECK_ID_IDREF, null)
    val bs = ArrayBuffer[XmlValidationError]()
    val entityResolver = new EntityResolver {
      override def resolveEntity(publicId: String, systemId: String): InputSource =
        new InputSource(new StringReader(""))
    }
    val errorHandler: ErrorHandler = new ErrorHandler {
      override def warning(e: SAXParseException): Unit = addError(e)
      override def error(e: SAXParseException): Unit = addError(e)
      override def fatalError(e: SAXParseException): Unit = addError(e)
      private def addError(e: SAXParseException): Unit =
        bs.append(XmlValidationError(e.getLineNumber, e.getColumnNumber, e.getMessage))
    }
    props.put(ValidateProperty.ERROR_HANDLER, errorHandler)
    props.put(ValidateProperty.ENTITY_RESOLVER, entityResolver)
    try {
      val driver = new ValidationDriver(props.toPropertyMap)
      driver.loadSchema(schemaSource(schema))
      driver.validate(is)
      bs.toList
    } catch {
      case e: SAXParseException =>
        Seq(XmlValidationError(e.getLineNumber, e.getColumnNumber, e.getLocalizedMessage))
    } finally {
      bs.clear()
    }
  }(exec)
}
