package services.harvesting

import java.io.StringWriter

import helpers.TestConfiguration
import models.OaiRsConfig
import org.w3c.dom.Element
import play.api.Application
import play.api.test.PlaySpecification

class WSOaiRsClientSpec extends PlaySpecification with TestConfiguration {

  private def stringify(elem: Element): String = {
    import javax.xml.transform.TransformerFactory
    import javax.xml.transform.dom.DOMSource
    import javax.xml.transform.stream.StreamResult
    val s = new DOMSource(elem)
    val w = new StringWriter()
    val r = new StreamResult(w)
    val tf = TransformerFactory.newInstance()
    val t = tf.newTransformer()
    t.transform(s, r)
    w.toString
  }

  private def endpoint(implicit app: Application) = {
    OaiRsConfig(java.net.URI.create("https://collections.ushmm.org/resourcesync/ushmm/sitemaps/capabilitylist.xml"))
  }

  "OAI RS client service" should {
    "list items" in new ITestApp {
      val client = inject[OaiRsClient]

      val list = await(client.list(endpoint))
      println(list)

      success

    }
  }
}
