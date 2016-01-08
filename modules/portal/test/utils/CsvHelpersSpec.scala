package utils

import play.api.test.PlaySpecification

class CsvHelpersSpec extends PlaySpecification {

  "CsvHelpers" should {
    "only quote when necessary" in {
      val csv = CsvHelpers.writeCsv(Seq("col1", "col2"), Seq(Array("val1", "val2, comma")))
      csv must_== "col1,col2\nval1,\"val2, comma\"\n"
    }

    "not unnecessarily quote unicode values" in {
      val csvNoQuotes = CsvHelpers.writeCsv(Seq("col1"), Seq(Array("a long string with some unicode: državno")))
      csvNoQuotes must_== "col1\na long string with some unicode: državno\n"

      val csvWithQuotes = CsvHelpers.writeCsv(Seq("col1"), Seq(Array("a long string with some unicode+comma: drža,vno")))
      csvWithQuotes must_== "col1\n\"a long string with some unicode+comma: drža,vno\"\n"
    }
  }
}
