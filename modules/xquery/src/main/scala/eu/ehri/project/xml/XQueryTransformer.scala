package eu.ehri.project.xml

import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import eu.ehri.project.xml.BaseXXQueryXmlTransformer.using
import org.slf4j.{Logger, LoggerFactory}
import scopt.OParser

import java.io.{File, FileNotFoundException}
import scala.io.Source

/**
  * Command-line interface to the BaseXXQueryTransformer
  */
object XQueryTransformer {

  val PROG_NAME = "xmlmapper"
  val VERSION = "0.0.1"

  case class Options(
    tsv: File = new File("."),
    input: File = new File("."),
    functions: Option[File] = None,
    script: Option[File] = None,
    params: Map[String,String] = Map.empty,
    printScript: Boolean = false,
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
          .text("Path to the input XML file."),
        opt[File]('t', "tsv")
          .required()
          .action ((s, opt) => opt.copy(tsv = s))
          .text("Path to the tsv mapping configuration, including headers."),
        // Option input
        opt[Option[File]]('f', "functions")
          .action((s, opt) => opt.copy(functions = s))
          .text("""Path to an XQuery module (.xqm) containing additional functions in the 'xtra' namespace.
                  |This can be omitted if no xtra:functions are called in the mapping.""".stripMargin),
        opt[Option[File]]('s', "script")
          .action((s, opt) => opt.copy(script = s))
          .text("Path to override the default XQuery runner script (.xqy)."),
        opt[Map[String, String]]('n', "namespaces")
          .action((p, opt) => opt.copy(params = p))
          .text("Additional namespaces in prefix1=url1,prefix2=url2 format."),
        opt[Unit]("print-script")
          .action((_, opt) => {
            println(using(Source.fromResource("transform.xqy"))(_.mkString))
            System.exit(1)
            opt
          })
          .text(
            """Print out the default transformation script and exit.
              |This can be useful if you want to save it as a file to modify and
              |use with the -s|script <script-file.xqy> option.""".stripMargin),
        opt[Unit]("print-functions")
          .action((_, opt) => {
            println(using(Source.fromResource("xtra.xqm"))(_.mkString))
            System.exit(1)
            opt
          })
          .text(
            """Print out the default xtra functions module and exit.
              |This can be useful if you want to save it as a file to modify and
              |use with the -f|functions <module-file.xqm> option.""".stripMargin),
        opt[Unit]('d', "debug")
          .action((_, opt) => opt.copy(debug = true))
          .text("Enable debug output."),
        help('h', "help").text("Prints this usage text."),
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

        try {
          val input = using(Source.fromFile(opts.input))(_.mkString)
          val mapping = using(Source.fromFile(opts.tsv))(_.mkString)
          val funcsOpt = opts.functions.map(_.toURI)
          val scriptOpt: Option[String] = opts.script.map(f => using(Source.fromFile(f))(_.mkString))

          val transformer = BaseXXQueryXmlTransformer(scriptOpt, funcsOpt)
          println(transformer.transform(input, mapping, opts.params))
        } catch {
          case e: FileNotFoundException =>
            System.err.println("Error: " + e.getMessage)
            System.exit(2)
        }
      case None =>
        // Error message
    }
  }
}
