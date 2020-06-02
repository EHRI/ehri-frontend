package services.ingest

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.xml.Characters
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import play.api.test.PlaySpecification

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
