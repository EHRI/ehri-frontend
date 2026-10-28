package eu.ehri.project.xml

import net.sf.saxon.Configuration
import net.sf.saxon.expr.XPathContext
import net.sf.saxon.functions.registry.BuiltInFunctionSet
import net.sf.saxon.lib._
import net.sf.saxon.om.NamespaceUri
import net.sf.saxon.s9api._
import net.sf.saxon.trans.XPathException
import org.slf4j.LoggerFactory
import org.xml.sax.SAXParseException
import play.api.libs.json._

import java.io.{StringReader, StringWriter}
import javax.inject.Inject
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.{ErrorListener, Result, TransformerConfigurationException, TransformerException}

object SaxonXsltXmlTransformer {

  private val logger = LoggerFactory.getLogger(SaxonXsltXmlTransformer.getClass)

  // Functions that stylesheets cannot call. fn:transform can run another
  // stylesheet with a new, unrestricted, configuration given as an option.
  private val deniedFunctions: Set[String] = Set("transform")

  private def denied(what: String): XPathException =
    new XPathException(s"Access to external resources is not allowed: $what")

  /**
    * A view of a built-in function set without the denied functions. Since
    * dynamic lookups (e.g. via fn:function-lookup) use the same function
    * sets, these are also unable to find them.
    */
  private class FilteredFunctionSet(delegate: BuiltInFunctionSet) extends BuiltInFunctionSet {
    importFunctionSet(delegate)

    override def getFunctionDetails(name: String, arity: Int): BuiltInFunctionSet.Entry =
      if (deniedFunctions.contains(name)) null else super.getFunctionDetails(name, arity)
    override def getNamespace: NamespaceUri = delegate.getNamespace
    override def getConventionalPrefix: String = delegate.getConventionalPrefix
  }

  /**
    * Stylesheets are user input, so this configuration prevents them from
    * accessing anything outside the transformation: files and URLs (via
    * document(), doc(), unparsed-text(), json-doc(), collection(), xsl:include,
    * etc.), environment variables, writing output with xsl:result-document,
    * and running other stylesheets with fn:transform. External entities and
    * DTDs in the input are replaced with empty content.
    */
  private class SandboxConfiguration extends Configuration {
    private val xsltSets = new java.util.concurrent.ConcurrentHashMap[Int, BuiltInFunctionSet]()
    private val xpathSets = new java.util.concurrent.ConcurrentHashMap[Int, BuiltInFunctionSet]()

    override def getXSLTFunctionSet(version: Int): BuiltInFunctionSet =
      xsltSets.computeIfAbsent(version, v => new FilteredFunctionSet(super.getXSLTFunctionSet(v)))
    override def getXPathFunctionSet(version: Int): BuiltInFunctionSet =
      xpathSets.computeIfAbsent(version, v => new FilteredFunctionSet(super.getXPathFunctionSet(v)))

    // No URI schemes may be accessed directly...
    setConfigurationProperty(Feature.ALLOWED_PROTOCOLS, "")
    setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, java.lang.Boolean.FALSE)

    // ...and any resource requests are refused, except for entities and DTDs,
    // which are skipped.
    setResourceResolver((request: ResourceRequest) => request.nature match {
      case ResourceRequest.EXTERNAL_ENTITY_NATURE | ResourceRequest.DTD_NATURE =>
        logger.warn(s"Skipping entity resolution: '${request.uri}' '${request.publicId}'")
        new StreamSource(new StringReader(""))
      case _ => throw denied(request.uri)
    })
    setUnparsedTextURIResolver((uri: java.net.URI, _: String, _: Configuration) => throw denied(uri.toString))
    setCollectionFinder((_: XPathContext, uri: String) => throw denied(uri))
    setOutputURIResolver(new OutputURIResolver {
      override def newInstance(): OutputURIResolver = this
      override def resolve(href: String, base: String): Result = throw denied(href)
      override def close(result: Result): Unit = ()
    })
    setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, new EnvironmentVariableResolver {
      override def getAvailableEnvironmentVariables: java.util.Set[String] = java.util.Collections.emptySet()
      override def getEnvironmentVariable(name: String): String = null
    })
  }

  // Processors are thread-safe, so one can be shared by all transformations.
  private lazy val processor: Processor = new Processor(new SandboxConfiguration)
}

case class SaxonXsltXmlTransformer @Inject()() extends XsltXmlTransformer {

  import SaxonXsltXmlTransformer._

  // TODO: Cache compiled stylesheet against mapping and parameters?

  def transform(input: String, mapping: String, params: JsObject = Json.obj()): String = {
    if (mapping.trim.isEmpty) input else {
      val writer = new StringWriter()
      val mapSource = new StreamSource(new StringReader(mapping))
      val inputSource = new StreamSource(new StringReader(stripUTF8BOM(input)))

      val compiler = processor.newXsltCompiler()
      // Keep compilation errors, since the exception thrown only says that there were some
      val compileErrors = scala.collection.mutable.ListBuffer.empty[String]
      compiler.setErrorReporter((error: XmlProcessingError) => {
        logger.error(error.getMessage)
        if (!error.isWarning) compileErrors += error.getMessage
      })

      params.fields.foreach {
        case (field, JsString(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value))
        case (field, JsNumber(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value.bigDecimal))
        case (field, JsBoolean(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value))
        case (_, JsNull) => // Ignore value...
        case (field, _) => throw XsltConfigError(s"Parameter key '$field' has an unsupported type, " +
          s"currently only string, number, and boolean can be used")
      }

      try {
        val stylesheet = compiler.compile(mapSource)
        val out = processor.newSerializer(writer)
        out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes")
        out.setOutputProperty(Serializer.Property.INDENT, "yes")

        val transformer = stylesheet.load30()
        transformer.setErrorListener(new ErrorListener {
          override def warning(exception: TransformerException): Unit = logger.warn(exception.getMessage)
          override def error(exception: TransformerException): Unit = logger.error(exception.getMessage)
          override def fatalError(exception: TransformerException): Unit = logger.error(exception.getMessage)
        })

        transformer.transform(inputSource, out)
        writer.toString
      } catch {
        case _: SaxonApiException if compileErrors.nonEmpty => throw XsltConfigError(compileErrors.mkString("; "))
        case e: SaxonApiException => throw XsltConfigError(e.getMessage)
        case e: TransformerConfigurationException => throw XsltConfigError(e.getMessage)
        case e: SAXParseException => throw XmlTransformationError(e.getLineNumber, e.getColumnNumber, e.getMessage)
      }
    }
  }
}
