package models

import org.specs2.mutable.Specification

import java.time.Instant

class ImportDatasetSpec extends Specification {

  private def ds(contentType: Option[String]) =
    ImportDataset("r1", "default", "Default", ImportDataset.Src.Upload, contentType = contentType, created = Instant.now())

  "ImportDataset" should {
    "give its media type without parameters" in {
      ds(Some("text/csv")).mediaType must beSome("text/csv")
      ds(Some("text/csv; charset=windows-1252")).mediaType must beSome("text/csv")
      ds(Some(" Application/JSON ;charset=utf-8")).mediaType must beSome("application/json")
      ds(Some("")).mediaType must beNone
      ds(None).mediaType must beNone
    }
  }
}
