package services.ingest

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.{XmlParsing, XmlWriting}
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import play.api.test.PlaySpecification

import scala.concurrent.Future

class OaiPmhTokenParserSpec extends PlaySpecification {
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
      </ListSets>
    </OAI-PMH>""".stripMargin

  "OAI PMH parser" should {
    "materialize with resumption token" in {
      val bytes = ByteString.fromString(testString)
      val src: Source[ByteString, Future[TokenState]] = Source.single(bytes)
        .via(XmlParsing.parser)
        .viaMat(OaiPmhTokenParser.parser)(Keep.right)
        .via(XmlWriting.writer)

      val (rt, psrc) = src.preMaterialize()
      await(psrc.runFold(ByteString.empty)(_ ++ _))
      await(rt) must_== Resume("bar")
    }
  }
}
