package defines

import java.time.LocalDateTime

import play.api.test.PlaySpecification

class BindersSpec extends PlaySpecification {
  "Binder" should {
    val binder = defines.binders.dateTimeQueryBinder

    "allow binding from partial dates in queries" in {
      binder.bind("date", Map("date" -> Seq("1949-10"))) must_== Some(Right(LocalDateTime.parse("1949-10-01T00:00")))
      binder.bind("date", Map("date" -> Seq("1949-10-01"))) must_== Some(Right(LocalDateTime.parse("1949-10-01T00:00")))
      binder.bind("date", Map("date" -> Seq("1949-10-01T10:10:10"))) must_== Some(Right(LocalDateTime.parse("1949-10-01T10:10:10")))
    }

    "give an error with invalid dates" in {
      binder.bind("date", Map("date" -> Seq("foo"))) must beSome.which { d =>
        d must_== Left("Invalid date format: foo")
      }
    }
  }
}
