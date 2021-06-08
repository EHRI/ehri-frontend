package services.transformation

import akka.stream.Materializer
import com.google.common.io.Resources
import org.basex.core.Context
import org.basex.io.IOStream
import org.basex.query.util.UriResolver
import org.basex.query.{QueryException, QueryProcessor}
import play.api.libs.json.{JsBoolean, JsNull, JsNumber, JsObject, JsString, Json}
import play.api.{Environment, Logger}

import java.io.{ByteArrayOutputStream, IOException}
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.concurrent.ExecutionContext

//noinspection UnstableApiUsage
object BaseXXQueryXmlTransformer {
  def using[T <: AutoCloseable, R](t: T)(f2: T => R): R =
    try f2(t) finally t.close()

  final val uriResolver: UriResolver = (path, _, _) =>
    new IOStream(Resources.getResource(path).openStream())

  val INPUT = "input"
  val MAPPING = "mapping"
  val NAMESPACES = "namespaces"
  val LIB_URI = "libURI"
  val RESERVED = Seq(INPUT, MAPPING, NAMESPACES, LIB_URI)
}

//noinspection UnstableApiUsage
case class BaseXXQueryXmlTransformer @Inject()(env: Environment)(implicit ec: ExecutionContext, mat: Materializer)
  extends XQueryXmlTransformer with Timer {

  import BaseXXQueryXmlTransformer._

  val script: String = readResource("transform.xqy")
  val utilLibUrl: URI = URI.create(Resources.getResource("xtra.xqm").toString)

  def readResource(name: String): String =
    Resources.toString(env.resource(name)
      .getOrElse(throw new IOException(s"Missing resource: $name")), StandardCharsets.UTF_8)

  protected val logger: Logger = Logger(classOf[BaseXXQueryXmlTransformer])

  @throws(classOf[XmlTransformationError])
  override def transform(data: String, map: String, params: JsObject = Json.obj()): String = {

    // Check parameters won't overwrite reserved params
    params.keys.foreach { key =>
      if (RESERVED.contains(key)) {
        throw InvalidMappingError(s"Parameter key '$key' is a reserved value")
      }
    }

    import org.basex.query.value.`type`.AtomType
    import org.basex.query.value.item.{Str => BaseXString}
    import org.basex.query.value.map.{Map => BaseXMap}
    try {
      logger.trace(s"Input: $data")
      time("Transformation") {
        using(new QueryProcessor(script, new Context()).uriResolver(uriResolver)) { proc =>
          var ns = BaseXMap.EMPTY
          Map(
            "" -> "urn:isbn:1-931666-22-9",
            "xlink" -> "http://www.w3.org/1999/xlink",
            "xsi" -> "http://www.w3.org/2001/XMLSchema-instance"
          ).foreach { case (k, v) =>
            ns = ns.put(
              new BaseXString(k.getBytes, AtomType.STR),
              new BaseXString(v.getBytes, AtomType.STR),
              null
            )
          }
          proc.bind(INPUT, data, "xs:string")
          proc.bind(MAPPING, map, "xs:string")
          proc.bind(NAMESPACES, ns, "map()")
          proc.bind(LIB_URI, utilLibUrl, "xs:anyURI")

          // bind additional data from parameters.
          params.fields.foreach {
            case (field, JsString(value)) => proc.bind(field, value, "xs:string")
            case (field, JsNumber(value)) => proc.bind(field, value, "xs:decimal")
            case (field, JsBoolean(value)) => proc.bind(field, value, "xs:boolean")
            case (_, JsNull) => // Ignore nulls
            case (field, _) => throw InvalidMappingError(s"Parameter key '$field' has an unsupported type, " +
              s"currently only string, number, and boolean can be used")
          }

          logger.debug(s"Module URL: $utilLibUrl")

          val iter = proc.iter()

          val bytes = new ByteArrayOutputStream()
          using(proc.getSerializer(bytes)) { ser =>
            // Iterate through all items and serialize contents
            var item = iter.next()
            while (item != null) {
              ser.serialize(item)
              item = iter.next()
            }
          }
          bytes.flush()
          bytes.toString()
        }
      }
    } catch {
      case e: QueryException =>
        // NB: Line numbers here are useless since they refer to the transformation
        // script and not the CSV, which is the actual user input
        throw InvalidMappingError(e.getLocalizedMessage)
    }
  }
}
