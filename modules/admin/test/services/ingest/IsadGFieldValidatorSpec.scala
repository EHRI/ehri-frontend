package services.ingest

import org.specs2.mutable.Specification

class IsadGFieldValidatorSpec extends Specification {

  private val validator = IsadGFieldValidator()

  "ISAD(G) field validator" should {

    "pass a complete, valid field set" in {
      validator.validateFields(Seq("objectIdentifier", "languageCode", "name", "ref", "scopeAndContent")) must beEmpty
    }

    "report each missing required field" in {
      val errs = validator.validateFields(Seq("scopeAndContent"))
      errs.map(_.field) must containTheSameElementsAs(Seq("objectIdentifier", "languageCode", "name"))
      errs.map(_.kind).distinct must_== Seq("missing")
    }

    "report unexpected fields" in {
      val errs = validator.validateFields(Seq("objectIdentifier", "languageCode", "name", "bogusField"))
      errs must_== Seq(FieldValidationError("bogusField", "unexpected", "Unexpected field 'bogusField'"))
    }

    "treat 'identifier' as unexpected - only 'objectIdentifier' is recognised" in {
      val errs = validator.validateFields(Seq("identifier", "languageCode", "name"))
      errs must contain(FieldValidationError("objectIdentifier", "missing", "Required field 'objectIdentifier' is missing"))
      errs must contain(FieldValidationError("identifier", "unexpected", "Unexpected field 'identifier'"))
    }

    "recognise archivistNote, not archivistsNote" in {
      validator.validateFields(Seq("objectIdentifier", "languageCode", "name", "archivistNote")) must beEmpty
      val errs = validator.validateFields(Seq("objectIdentifier", "languageCode", "name", "archivistsNote"))
      errs must_== Seq(FieldValidationError("archivistsNote", "unexpected", "Unexpected field 'archivistsNote'"))
    }

    "recognise sourceFileId and an access point field for every AccessPointType" in {
      val extra = Seq(
        "sourceFileId", "personAccessPoint", "familyAccessPoint", "corporateBodyAccessPoint",
        "creatorAccessPoint", "subjectAccessPoint", "placeAccessPoint", "genreAccessPoint")
      validator.validateFields(Seq("objectIdentifier", "languageCode", "name") ++ extra) must beEmpty
    }
  }
}
