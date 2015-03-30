package utils.ead;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */

import org.apache.xml.serialize.Method;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.io.StringWriter;


/**
 * Pretty-prints xml, supplied as a string.
 * <p/>
 * eg.
 * <code>
 * String formattedXml = XmlFormatter.format("<tag><nested>hello</nested></tag>");
 * </code>
 */
public class XmlFormatter {
    // FIXME: This should be the (much more verbose) non-deprecated
    // method of tidying an XML document, but due to some unknown
    // bug only seems to work half the time. It also insists on
    // outputting in UTF-16 rather than UTF-8.
    public static String format2(String unformattedXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOMImplementationLS domImpl =
                    (DOMImplementationLS)registry.getDOMImplementation("LS");
            final LSSerializer writer = domImpl.createLSSerializer();
            writer.getDomConfig().setParameter("format-pretty-print", true);
            return writer.writeToString(
                    builder.parse(new InputSource(new StringReader(unformattedXml))));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String format(String unformattedXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            StringWriter stringWriter = new StringWriter();
            XMLSerializer serializer = new XMLSerializer(stringWriter, new OutputFormat(Method.XML, "UTF-8", true));
            serializer.serialize(builder.parse(new InputSource(new StringReader(unformattedXml))));
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}