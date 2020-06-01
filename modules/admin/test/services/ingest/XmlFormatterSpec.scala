package services.ingest

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.{XmlParsing, XmlWriting}
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import play.api.test.PlaySpecification

import scala.concurrent.Future

class XmlFormatterSpec extends PlaySpecification {
  private implicit val as: ActorSystem = ActorSystem("test")
  private implicit val mat: Materializer = Materializer.createMaterializer(as)

  private val testString = """
    <OAI-PMH>
      <ListSets>
        <set>
          <setSpec>foo</setSpec>
          <setName>Foo</setName>
        </set>
        <resumptionToken>bar</resumptionToken>
        <empty/>
        <text>
          Hello, world, this is
          some text.

          With whitespace in
          it.
        </text>
      </ListSets>
    </OAI-PMH>""".stripMargin

  "XML Formatter" should {
    "format a stream of parse events" in {
      val bytes = ByteString.fromString(testString)
      val src: Source[ByteString, _] = Source.single(bytes)
        .via(XmlParsing.parser)
        .via(XmlFormatter.format)
        .via(XmlWriting.writer)

      println(await(src.runFold(ByteString.empty)(_ ++ _)).utf8String)

      success
    }
  }
}
