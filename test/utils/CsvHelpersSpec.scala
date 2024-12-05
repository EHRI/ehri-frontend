package utils

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import play.api.test.PlaySpecification

import scala.concurrent.Future

class CsvHelpersSpec extends PlaySpecification {

  private implicit val sys: ActorSystem = ActorSystem("MyTest")
  private implicit val mat: Materializer = Materializer(sys)

  def toString(src: Source[String, _]): Future[String] = src.runFold("")(_ + _)

  "CsvHelpers" should {
    "only quote when necessary" in {
      val csv = await(toString(CsvHelpers.writeCsv(Seq("col1", "col2"), Seq(Array("val1", "val2, comma")))))
      csv must_== "col1,col2\r\nval1,\"val2, comma\"\r\n"
    }

    "not unnecessarily quote unicode values" in {
      val csvNoQuotes = await(toString(CsvHelpers.writeCsv(Seq("col1"), Seq(Array("a long string with some unicode: državno")))))
      csvNoQuotes must_== "col1\r\na long string with some unicode: državno\r\n"

      val csvWithQuotes = await(toString(CsvHelpers.writeCsv(Seq("col1"), Seq(Array("a long string with some unicode+comma: drža,vno")))))
      csvWithQuotes must_== "col1\r\n\"a long string with some unicode+comma: drža,vno\"\r\n"
    }
  }
}
