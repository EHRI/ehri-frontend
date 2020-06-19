package services.harvesting

import java.io.StringWriter

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import helpers.TestConfiguration
import models.OaiPmhIdentity.Granularity
import models.{OaiPmhConfig, OaiPmhIdentity}
import org.w3c.dom.Element
import play.api.test.PlaySpecification
import play.api.{Application, Configuration}

import scala.xml.XML

class OaiPmhClientServiceSpec extends PlaySpecification with TestConfiguration {

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
    val config = app.injector.instanceOf[Configuration]
    OaiPmhConfig(s"${utils.serviceBaseUrl("ehridata", config)}/oaipmh", "ead")
  }

  "OAI PMH client service" should {
    "identify" in new ITestApp {
      val client = inject[OaiPmhClient]
      val ident = await(client.identify(endpoint))
      ident must_== OaiPmhIdentity(
        name = "EHRI",
        url = "http://example.com",
        version = "2.0",
        granularity = Granularity.Second
      )
    }

    "list sets" in new ITestApp {
      val client = inject[OaiPmhClient]
      val sets = await(client.listSets(endpoint).runWith(Sink.seq))
      sets must_== Seq(("nl", "Netherlands"), ("nl:r1", "NIOD Description"))
    }

    "list identifiers" in new ITestApp {
      val client = inject[OaiPmhClient]
      val (idents, next) = await(client.listIdentifiers(endpoint))
      idents.sortBy(_._1) must_== Seq("c4" -> false, "nl-r1-m19" -> false)
      next must beNone
    }

    "get records" in new ITestApp {
      val client = inject[OaiPmhClient]
      val item = await(client.getRecord(endpoint, "c4")
        .runFold(ByteString.empty)(_ ++ _)).utf8String
      val xml = XML.loadString(item)
      (xml \ "eadheader" \ "eadid").text must_== "c4"
    }

    "list records" in new ITestApp {
      val client = inject[OaiPmhClient]
      val records = await(client.listRecords(endpoint).runWith(Sink.seq))
        .map(e => XML.loadString(stringify(e)))
        .sortBy(e => (e \ "eadheader" \ "eadid").text)
      records.size must_== 2
      (records.head \ "eadheader" \ "eadid").text must_== "c4"
      (records(1) \ "eadheader" \ "eadid").text must_== "nl-r1-m19"
    }
  }
}
