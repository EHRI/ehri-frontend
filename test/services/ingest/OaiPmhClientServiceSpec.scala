package services.ingest

import java.io.StringWriter

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import helpers.TestConfiguration
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import models.admin.{OaiPmhConfig, OaiPmhIdentity}
import play.api.test.PlaySpecification
import play.api.{Application, Configuration}

class OaiPmhClientServiceSpec extends PlaySpecification with TestConfiguration {

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
        version = "2.0"
      )
    }

    "list sets" in new ITestApp {
      val client = inject[OaiPmhClient]
      val sets = await(client.listSets(endpoint).runWith(Sink.seq))
      sets must_== Seq(("nl", "Netherlands"), ("nl:r1", "NIOD Description"))
    }

    "list identifiers" in new ITestApp {
      val client = inject[OaiPmhClient]
      val idents = await(client.listIdentifiers(endpoint).runWith(Sink.seq))
      idents.sorted must_== Seq("c4", "nl-r1-m19")
    }

    "get records" in new ITestApp {
      val client = inject[OaiPmhClient]
      val item = await(client.getRecord(endpoint, "c4").runFold(ByteString.empty)(_ ++ _)).utf8String
      print(item)
      success
    }

    "list records" in new ITestApp {
      val client = inject[OaiPmhClient]
      val records = await(client.listRecords(endpoint).runWith(Sink.seq))
      println(records.map { elem =>
        val s = new DOMSource(elem)
        val w = new StringWriter()
        val r = new StreamResult(w)
        val tf = TransformerFactory.newInstance()
        val t = tf.newTransformer()
        t.transform(s, r)
        w.toString
      })
      success
    }

    "stream records" in new ITestApp {
//      val client = inject[OaiPmhClient]
//      client.streamRecords(endpoint)
//        .via(XmlParsing.subslice(Seq("OAI-PMH", "ListRecords", "record")))
//        .splitWhen(e => e match {
//          case StartElement(localName, _, _, _, _) if localName == "record" => true
//          case _ => false
//        }).via(OaiPmhRecordParser.parser)
    }
  }
}
