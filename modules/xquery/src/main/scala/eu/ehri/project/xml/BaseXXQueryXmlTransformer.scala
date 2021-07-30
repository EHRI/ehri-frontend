package eu.ehri.project.xml

import org.basex.core.Context
import org.basex.io.IOStream
import org.basex.query.util.UriResolver
import org.basex.query.{QueryException, QueryProcessor}
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayOutputStream
import java.net.URI

object BaseXXQueryXmlTransformer {
  def using[T <: AutoCloseable, R](t: T)(f2: T => R): R =
    try f2(t) finally t.close()

  final val uriResolver: UriResolver = (path, _, _) =>
    new IOStream(getClass.getResource("/" + path).openStream())

  val INPUT = "input"
  val MAPPING = "mapping"
  val NAMESPACES = "namespaces"
  val NS_STRING = "nsString"
  val LIB_URI = "libURI"

  val DEFAULT_NS = Map(
    "" -> "urn:isbn:1-931666-22-9",
    "xlink" -> "http://www.w3.org/1999/xlink",
    "xsi" -> "http://www.w3.org/2001/XMLSchema-instance",
    "oai" -> "http://www.openarchives.org/OAI/2.0/",
    "oai_dc" -> "http://www.openarchives.org/OAI/2.0/oai_dc/",
    "dc" -> "http://purl.org/dc/elements/1.1/"
  )
}

case class BaseXXQueryXmlTransformer(scriptOpt: Option[String] = None, funcOpt: Option[URI] = None) extends XQueryXmlTransformer with Timer {

  private val logger: Logger = LoggerFactory.getLogger(classOf[BaseXXQueryXmlTransformer])
  override def logTime(s: String): Unit = logger.debug(s)
  import BaseXXQueryXmlTransformer._

  val script: String = scriptOpt.getOrElse {
    using(scala.io.Source.fromResource("transform.xqy"))(_.mkString)
  }
  val utilLibUrl: URI = funcOpt.getOrElse(getClass.getResource("/xtra.xqm").toURI)

  @throws(classOf[InvalidMappingError])
  override def transform(data: String, map: String, params: Map[String, String] = Map.empty): String = {

    import org.basex.query.value.`type`.AtomType
    import org.basex.query.value.item.{Str => BaseXString}
    import org.basex.query.value.map.{Map => BaseXMap}
    try {
      logger.trace(s"Input: $data")
      logger.trace(s"Mapping: $map")
      logger.trace(s"Params: $params")
      time("Transformation") {
        using(new QueryProcessor(script, new Context()).uriResolver(uriResolver)) { proc =>
          var ns = BaseXMap.EMPTY
          DEFAULT_NS.foreach { case (k, v) =>
            ns = ns.put(
              new BaseXString(k.getBytes, AtomType.STR),
              new BaseXString(v.getBytes, AtomType.STR),
              null
            )
          }

          params
            .foreach { case (k, v) =>
              ns = ns.put(
                new BaseXString(k.getBytes, AtomType.STR),
                new BaseXString(v.getBytes, AtomType.STR),
                null
              )
            }

          val allNs = DEFAULT_NS ++ params
          logger.debug(s"Available namespaces: ${allNs.map(f => f._1 + ":" + f._2).mkString(", ")}")
          val nsString = allNs.filter(_._1.nonEmpty).map { case (k, v) => s"declare namespace $k='$v';"}.mkString("")

          proc.bind(INPUT, data, "xs:string")
          proc.bind(MAPPING, map, "xs:string")
          proc.bind(NAMESPACES, ns, "map()")
          proc.bind(NS_STRING, nsString, "xs:string")
          proc.bind(LIB_URI, utilLibUrl, "xs:anyURI")

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
