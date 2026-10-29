package eu.ehri.project.xml

import org.specs2.mutable.Specification

class BaseXTabularHeaderReaderSpec extends Specification {

  "Tabular header reader should" should {

    "read a CSV header row" in {
      val reader = BaseXTabularHeaderReader()
      val json = reader.readHeader("id,title,creator\n1,Letters,Anna\n", Map("input.separator" -> "comma"))
      json must_== """["id","title","creator"]"""
    }

    "read a TSV header row" in {
      val reader = BaseXTabularHeaderReader()
      val json = reader.readHeader("id\ttitle\tcreator\n1\tLetters\tAnna\n", Map("input.separator" -> "tab"))
      json must_== """["id","title","creator"]"""
    }

    "handle a quoted header value containing the separator" in {
      val reader = BaseXTabularHeaderReader()
      val json = reader.readHeader("id,\"Title, Long\",creator\n1,Letters,Anna\n", Map("input.separator" -> "comma"))
      json must_== """["id","Title, Long","creator"]"""
    }

    "default to comma separated when no option is given" in {
      val reader = BaseXTabularHeaderReader()
      val json = reader.readHeader("id,title,creator\n1,Letters,Anna\n")
      json must_== """["id","title","creator"]"""
    }
  }
}
