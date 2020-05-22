package services.ingest

import java.io.StringWriter

import akka.stream.scaladsl.Sink
import helpers.TestConfiguration
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import play.api.{Application, Configuration}
import play.api.test.PlaySpecification

class OaiPmhClientServiceSpec extends PlaySpecification with TestConfiguration {

  private def endpoint(implicit app: Application) = {
    val config = app.injector.instanceOf[Configuration]
    OaiPmhConfig(s"${utils.serviceBaseUrl("ehridata", config)}/oaipmh", "ead")
  }

  "OAI PMH client service" should {
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
  }
}
