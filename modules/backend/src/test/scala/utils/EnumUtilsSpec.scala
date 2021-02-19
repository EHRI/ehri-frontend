package utils

import org.specs2.mutable.Specification

class EnumUtilsSpec extends Specification {
  "tolerant enum seq binder should" should {
    import utils.EnumUtils.tolerantSeq
    object MyEnum extends scala.Enumeration {
      val a1 = Value("a1")
      val a2 = Value("a2")
      val a3 = Value("a3")
    }
    val binder = tolerantSeq(MyEnum)

    "bind from map" in {
      binder.bind(Map("[0]" -> "a1", "[1]" -> "a2")) must beRight.which { seq =>
        seq must equalTo(Seq(MyEnum.a1, MyEnum.a2))
      }
    }

    "ignore invalid values" in {
      binder.bind(Map("[0]" -> "a1", "[1]" -> "INVALID")) must beRight.which { seq =>
        seq must equalTo(Seq(MyEnum.a1))
      }
    }

    "unbind to map" in {
      binder.unbind(Seq(MyEnum.a1, MyEnum.a2)) must equalTo(Map("[0]" -> "a1", "[1]" -> "a2"))
    }
  }
}
