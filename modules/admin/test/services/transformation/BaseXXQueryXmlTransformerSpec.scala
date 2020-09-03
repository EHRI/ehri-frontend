package services.transformation

import akka.actor.ActorSystem
import akka.stream.Materializer
import helpers.ResourceUtils
import play.api.Environment
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext

class BaseXXQueryXmlTransformerSpec extends PlaySpecification with ResourceUtils {

  private implicit val ctx: ExecutionContext = scala.concurrent.ExecutionContext.global
  private implicit val as: ActorSystem = ActorSystem.create("test")
  private implicit val mat: Materializer = Materializer(as)

  private val testPayload = """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin

  "XML transformer should" should {
    "transform a simple file" in {
      val transformer = BaseXXQueryXmlTransformer(Environment.simple())
      val map = resourceAsString("simple-mapping.tsv")
      val out = transformer.transform(testPayload, map)
      out must contain("http://www.loc.gov")
    }

    "report errors with context" in {
      val transformer = BaseXXQueryXmlTransformer(Environment.simple())
      val map = resourceAsString("simple-mapping.tsv") +
        "\n/ead/eadheader/\teadid\t/ead/eadheader/eadid\tinvalid-func()"
      transformer.transform(testPayload, map) must throwA[XmlTransformationError].like {
        case e => e.getMessage must contain("at /ead: at /ead/eadheader: Unknown function: fn:invalid-func")
      }
    }
  }
}
