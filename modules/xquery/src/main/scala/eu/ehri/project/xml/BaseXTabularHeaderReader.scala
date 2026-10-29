package eu.ehri.project.xml

import org.basex.query.value.item.Str
import org.basex.query.value.map.XQMap
import org.basex.query.QueryException
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayOutputStream

case class BaseXTabularHeaderReader(scriptOpt: Option[String] = None) extends XQueryTabularHeaderReader with Timer {

  private val logger: Logger = LoggerFactory.getLogger(classOf[BaseXTabularHeaderReader])
  override def logTime(s: String): Unit = logger.debug(s)
  import BaseXXQueryXmlTransformer.{INPUT, using, withQuery}

  val script: String = scriptOpt.getOrElse {
    using(scala.io.Source.fromResource("read-header.xqy"))(_.mkString)
  }

  @throws(classOf[InvalidMappingError])
  override def readHeader(data: String, params: Map[String, String] = Map.empty): String = {
    try {
      time("ReadHeader") {
        withQuery(script, resolver = None) { proc =>
          val opts: XQMap = params.foldLeft(XQMap.empty()) { case (m, (k, v)) =>
            m.put(Str.get(k), Str.get(v))
          }
          proc.variable(INPUT, data, "xs:string")
          proc.variable("options", opts, "map(*)")

          val iter = proc.iter()
          val bytes = new ByteArrayOutputStream()
          using(proc.serializer(bytes)) { ser =>
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
        throw InvalidMappingError(e.getLocalizedMessage)
    }
  }
}
