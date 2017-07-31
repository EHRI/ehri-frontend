package utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import defines.EntityType
import play.api.test.PlaySpecification
import services.data.Constants.{FROM => TFROM, _}


class ParamsSpec extends PlaySpecification {
  "RangeParams" should {
    "bind correctly with tolerant defaults" in {
      RangeParams.queryStringBindable.bind("range", Map()) must_== Some(Right(RangeParams.empty))
      RangeParams.queryStringBindable
        .bind("range", Map(OFFSET_PARAM -> Seq("-1"))) must_== Some(Right(RangeParams.empty))
      RangeParams.queryStringBindable
        .bind("range_a", Map("a" + OFFSET_PARAM -> Seq("10"))) must_== Some(Right(RangeParams(10)))
      RangeParams.queryStringBindable
        .bind("range", Map(OFFSET_PARAM -> Seq("foo"), LIMIT_PARAM -> Seq("10"))) must_== Some(Right(RangeParams(limit = 10)))
    }
  }

  "PageParams" should {
    "bind correctly with tolerant defaults" in {
      import PageParams.PAGE_PARAM
      PageParams.queryStringBindable.bind("params", Map()) must_== Some(Right(PageParams.empty))
      PageParams.queryStringBindable
        .bind("params", Map(PAGE_PARAM -> Seq("-1"))) must_== Some(Right(PageParams.empty))
      PageParams.queryStringBindable
        .bind("params_a", Map("a" + PAGE_PARAM -> Seq("10"))) must_== Some(Right(PageParams(10)))
      PageParams.queryStringBindable
        .bind("params", Map(PAGE_PARAM -> Seq("foo"), LIMIT_PARAM -> Seq("10"))) must_== Some(Right(PageParams(limit = 10)))
    }
  }

  "SystemEventParams" should {
    "bind correctly with tolerant defaults" in {
      SystemEventParams.queryStringBindable.bind("params", Map()) must_== Some(Right(SystemEventParams.empty))
      SystemEventParams.queryStringBindable
        .bind("params", Map(TFROM -> Seq("2017-01-01"))) must_== Some(Right(SystemEventParams(from = Some(
        LocalDate.parse("2017-01-01").atStartOfDay()))))
      SystemEventParams.queryStringBindable
        .bind("params", Map(TFROM -> Seq("2017-01"))) must_== Some(Right(SystemEventParams(from = Some(
        LocalDate.parse("2017-01-01").atStartOfDay()))))
      SystemEventParams.queryStringBindable
        .bind("params_a", Map("a" + TFROM -> Seq("2017-01-01"))) must_== Some(Right(SystemEventParams(from = Some(
        LocalDate.parse("2017-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay()))))
      SystemEventParams.queryStringBindable
        .bind("params", Map(TFROM -> Seq("bad-date"))) must_== Some(Right(SystemEventParams.empty))
      SystemEventParams.queryStringBindable
        .bind("params", Map(ITEM_TYPE -> Seq("bad-item", EntityType.DocumentaryUnit.toString))) must_== Some(
        Right(SystemEventParams(itemTypes = Seq(EntityType.DocumentaryUnit))))
    }
  }
}
