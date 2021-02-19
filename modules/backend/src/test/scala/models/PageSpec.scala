package models

import org.specs2.mutable.Specification
import utils.Page

class PageSpec extends Specification {

  val data = 1 to 30

  val testPage1 = Page(items = data.take(10), offset = 0, limit = 10, total = 30)
  val testPage2 = Page(items = data.drop(10), offset = 10, limit = 40, total = 30)
  val testPage3 = Page(items = data.slice(20, 30), offset = 20, limit = 10, total = 30)

  "Page class" should {
    "provide correct offset, start, and end" in {
      testPage1.offset mustEqual 0
      testPage1.page mustEqual 1
      testPage1.start mustEqual 1
      testPage1.end mustEqual 10
      testPage2.end mustEqual 30
    }

    "calculate number of pages correctly" in {
      testPage1.numPages mustEqual 3
      testPage1.page mustEqual 1
      testPage2.numPages mustEqual 1
      testPage2.page mustEqual 2
      testPage3.numPages mustEqual 3
      testPage3.page mustEqual 3
    }

    "not error with 0 limit" in {
      (testPage1.copy(limit = 0).numPages must not).throwA[ArithmeticException]
    }
  }
}
