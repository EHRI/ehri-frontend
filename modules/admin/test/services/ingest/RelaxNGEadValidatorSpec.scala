package services.ingest

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.google.common.io.Resources
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext

class RelaxNGEadValidatorSpec extends PlaySpecification {

  private implicit val ctx: ExecutionContext = scala.concurrent.ExecutionContext.global
  private implicit val as: ActorSystem = ActorSystem.create("test")
  private implicit val mat: Materializer = Materializer(as)

  "EAD validator service" should {
    "validate a file" in {
      val validator = RelaxNGEadValidator()

      val file = Paths.get(Resources.getResource("valid-ead.xml").toURI)
      val errs = await(validator.validateEad(file))
      errs.size must_== 0
    }

    "validate an URL" in {
      val validator = RelaxNGEadValidator()

      val file = Uri(Resources.getResource("valid-ead.xml").toString)
      val errs = await(validator.validateEad(file))
      errs.size must_== 0
    }

    "validate a stream" in {
      val validator = RelaxNGEadValidator()

      val file = Resources.getResource("valid-ead.xml")
      val src = FileIO.fromPath(Paths.get(file.toURI))
      val errs = await(validator.validateEad(src))
      errs.size must_== 0
    }

    "report errors from a stream" in {
      val validator = RelaxNGEadValidator()

      val file = Resources.getResource("invalid-ead.xml")
      val src = FileIO.fromPath(Paths.get(file.toURI))
      val errs = await(validator.validateEad(src))
      errs.size must_== 1

      // Ensure we can repeat the validation and receive the same
      // number of errors.
      val src2 = FileIO.fromPath(Paths.get(file.toURI))
      val errs2 = await(validator.validateEad(src2))
      errs2.size must_== errs.size

      errs2.head.line must_== 8
      errs2.head.pos must_== 80
      errs2.head.error must contain("value of attribute \"mainagencycode\" is invalid")
    }
  }
}
