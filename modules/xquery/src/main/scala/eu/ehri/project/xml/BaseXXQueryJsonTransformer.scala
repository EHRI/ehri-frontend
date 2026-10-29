package eu.ehri.project.xml

import org.basex.query.QueryException
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayOutputStream
import java.net.URI

/**
  * Transform a JSON document into another JSON document using a tabular (TSV)
  * mapping, in the same spirit as [[BaseXXQueryXmlTransformer]] but operating on
  * JSON maps/arrays (via `fn:parse-json`) rather than XML nodes.
  *
  * The mapping has the columns: target-path, target-node, type, source-node, value.
  * The `type` column (one of `value`, `object`, `array`; default `value`) encodes
  * the shape of the output node, which JSON requires but XML does not.
  */
case class BaseXXQueryJsonTransformer(scriptOpt: Option[String] = None, funcOpt: Option[URI] = None) extends XQueryJsonTransformer with Timer {

  private val logger: Logger = LoggerFactory.getLogger(classOf[BaseXXQueryJsonTransformer])
  override def logTime(s: String): Unit = logger.debug(s)
  import BaseXXQueryXmlTransformer.{INPUT, LIB_URI, MAPPING, using, withQuery}

  val script: String = scriptOpt.getOrElse {
    using(scala.io.Source.fromResource("transform-json.xqy"))(_.mkString)
  }
  val utilLibUrl: URI = funcOpt.getOrElse(getClass.getResource("/xtra.xqm").toURI)

  @throws(classOf[InvalidMappingError])
  override def transform(data: String, map: String, params: Map[String, String] = Map.empty): String = {
    try {
      logger.trace(s"Input: $data")
      logger.trace(s"Mapping: $map")
      logger.trace(s"Params: $params")
      time("Transformation") {
        withQuery(script) { proc =>
          proc.variable(INPUT, data, "xs:string")
          proc.variable(MAPPING, map, "xs:string")
          proc.variable(LIB_URI, utilLibUrl, "xs:anyURI")

          logger.debug(s"Module URL: $utilLibUrl")

          val iter = proc.iter()

          val bytes = new ByteArrayOutputStream()
          using(proc.serializer(bytes)) { ser =>
            // Iterate through all items and serialize contents. The script itself
            // serializes the result to a JSON string, so items are plain strings.
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
        // script and not the TSV, which is the actual user input
        throw InvalidMappingError(e.getLocalizedMessage)
    }
  }
}
