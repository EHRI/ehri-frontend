package utils

import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class PageSpec extends PlaySpecification {

  val data = 1 to 30

  val testPage1 = Page(items = data.take(10), page = 1, count = 10, total = 30)
  val testPage2 = Page(items = data, page = 1, count = 40, total = 30)

  "Page class" should {
    "provide correct offset, start, and end" in {
      testPage1.offset mustEqual 0
      testPage1.start mustEqual 1
      testPage1.end mustEqual 10
      testPage2.end mustEqual 30
    }

    "calculate number of pages correctly" in {
      testPage1.numPages mustEqual 3
      testPage2.numPages mustEqual 1
      testPage2.hasMultiplePages must beFalse
    }

    "not error with 0 count" in {
      (testPage1.copy(count = 0).numPages must not).throwA[ArithmeticException]
    }
  }
}
