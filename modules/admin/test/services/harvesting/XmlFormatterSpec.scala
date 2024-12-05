package services.harvesting

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.xml.Characters
import org.apache.pekko.stream.connectors.xml.scaladsl.XmlParsing
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.test.PlaySpecification
import services.ingest.XmlFormatter

class XmlFormatterSpec extends PlaySpecification {
  private implicit val as: ActorSystem = ActorSystem("test")
  private implicit val mat: Materializer = Materializer.createMaterializer(as)

  private val testString = """<foo><bar>spam</bar><baz/></foo>""".stripMargin

  "XML Formatter" should {
    "format a stream of parse events" in {
      val bytes = ByteString.fromString(testString)
      val events = await(Source.single(bytes)
        .via(XmlParsing.parser)
        .via(XmlFormatter.format)
        .runWith(Sink.seq))
      events(1) must_== Characters("\n")
      events(3) must_== Characters("\n    ")
      events(7) must_== Characters("\n    ")
      events(10) must_== Characters("\n")
    }
  }
}
