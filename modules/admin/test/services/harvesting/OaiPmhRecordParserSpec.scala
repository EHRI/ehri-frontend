package services.harvesting

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.xml.scaladsl.{XmlParsing, XmlWriting}
import org.apache.pekko.stream.scaladsl.{Keep, Source}
import org.apache.pekko.util.ByteString
import play.api.test.PlaySpecification

import scala.concurrent.Future

class OaiPmhRecordParserSpec extends PlaySpecification {
  private implicit val as: ActorSystem = ActorSystem("test")
  private implicit val mat: Materializer = Materializer.createMaterializer(as)

  private val testString = """
    <record>
      <header>
        <identifier>foo</identifier>
      </header>
      <metadata>
        <ead>
          <eadheader>
            <identifier>bar</identifier>
          </eadheader>
          <archdesc>
            <did>
              <unitid>test</unitid>
            </did>
          </archdesc>
        </ead>
      </metadata>
    </record>""".stripMargin

  "OAI PMH record parser" should {
    "parse identifier as materialized value" in {
      val bytes = ByteString.fromString(testString)
      val src: Source[ByteString, Future[String]] = Source.single(bytes)
        .via(XmlParsing.parser)
        .viaMat(OaiPmhRecordParser.parser)(Keep.right)
        .via(XmlWriting.writer)

      val (rt, psrc) = src.preMaterialize()
      await(rt) must_== "foo"
      val meta = await(psrc.runFold(ByteString.empty)(_ ++ _)).utf8String.trim
      meta must startWith("<ead")
      meta must endWith("</ead>")
    }
  }
}
