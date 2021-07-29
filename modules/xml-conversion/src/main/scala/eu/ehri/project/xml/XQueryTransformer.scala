package eu.ehri.project.xml

import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsObject, JsString}
import scopt.OParser

import java.io.File
import scala.io.Source

/**
  * Command-line interface to the BaseXXQueryTransformer
  */
object XQueryTransformer {

  val PROG_NAME = "xmlmapper";
  val VERSION = "0.0.1"

  case class Options(
    tsv: File = new File("."),
    input: File = new File("."),
    functions: Option[File] = None,
    script: Option[File] = None,
    params: Map[String,String] = Map.empty,
    debug: Boolean = false,
  )

  def main(args: Array[String]): Unit = {

    val builder = OParser.builder[Options]
    val parser1 = {
      import builder._
      OParser.sequence(
        programName(PROG_NAME),
        head(PROG_NAME, VERSION),
        arg[File]("<file>")
          .required()
          .maxOccurs(1)
          .action((s, opt) => opt.copy(input = s))
          .text("Path to the input XML file"),
        opt[File]('t', "tsv")
          .required()
          .action ((s, opt) => opt.copy(tsv = s))
          .text("Path to the tsv mapping configuration, including headers"),
        // Option input
        opt[Option[File]]('f', "functions")
          .action((s, opt) => opt.copy(functions = s))
          .text("""Path to an XQuery module (.xqm) containing additional functions in the 'xtra' namespace.
                  |This can be omitted if no xtra:functions are called in the mapping.""".stripMargin),
        opt[Option[File]]('s', "script")
          .action((s, opt) => opt.copy(script = s))
          .text("Path to override the default XQuery runner script (.xqy)"),
        opt[Map[String, String]]('n', "namespaces")
          .action((p, opt) => opt.copy(params = p))
          .text("Additional namespaces in prefix1=url1,prefix2=url2 format"),
        opt[Unit]('d', "debug")
          .action((_, opt) => opt.copy(debug = true))
          .text("Enable debug output"),
        help('h', "help").text("Prints this usage text"),
      )
    }

    OParser.parse(parser1, args, Options()) match {
      case Some(opts) =>
        val logger: Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
        logger match {
          case logback: LogbackLogger =>
            val level = if (opts.debug) Level.DEBUG else Level.ERROR
            logback.setLevel(level)
          case _ =>
        }

        import BaseXXQueryXmlTransformer.using

        val input = using(Source.fromFile(opts.input))(_.mkString)
        val mapping = using(Source.fromFile(opts.tsv))(_.mkString)
        val funcsOpt = opts.functions.map(_.toURI)
        val scriptOpt: Option[String] = opts.script.map(f => using(Source.fromFile(f))(_.mkString))
        val params: JsObject = JsObject(opts.params.map {case (s, v) => s -> JsString(v)})

        val transformer = BaseXXQueryXmlTransformer(scriptOpt, funcsOpt)
        try System.out.println(transformer.transform(input, mapping, params)) catch {
          case e: InvalidMappingError =>
            System.err.println(e.getMessage)
          case e: XmlTransformationError =>
            System.err.printf("Error: %s\n", e.getMessage)
        }
      case None =>
        // Error message
    }
  }
}
