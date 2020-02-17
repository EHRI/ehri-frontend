package services.ingest

import java.nio.file.Paths

import com.google.common.io.Resources
import play.api.test.PlaySpecification

class EadValidatorServiceSpec extends PlaySpecification {

  private implicit val ctx = scala.concurrent.ExecutionContext.global

  "EAD validator service" should {
    "return errors" in {
      val validator = EadValidatorService()

      val file = Paths.get(Resources.getResource("valid-ead.xml").toURI)
      val errs = await(validator.validateEad(file))
      errs.size must_== 0

      val errs2 = await(validator.validateEad(file))
      errs2.size must_== errs.size
    }
  }
}
