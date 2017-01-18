package utils.search

import defines.EntityType
import play.api.test.PlaySpecification

class SearchParamsSpec extends PlaySpecification {

  import SearchParams._

  "SearchParams" should {
    "bind correctly with tolerant defaults" in {
      SearchParams.searchParamsBinder.bind("params", Map()) must_== Some(Right(SearchParams.empty))
      SearchParams.searchParamsBinder
        .bind("params", Map(QUERY -> Seq("foo"))) must_== Some(Right(SearchParams(query = Some("foo"))))
      SearchParams.searchParamsBinder
        .bind("params", Map(QUERY -> Seq(""))) must_== Some(Right(SearchParams(query = None)))
      SearchParams.searchParamsBinder
        .bind("params_a", Map("a" + QUERY -> Seq("foo"))) must_== Some(Right(SearchParams(query = Some("foo"))))
      SearchParams.searchParamsBinder
        .bind("params", Map(ENTITY -> Seq("bad"))) must_== Some(Right(SearchParams.empty))
      SearchParams.searchParamsBinder
        .bind("params", Map(ENTITY -> Seq(EntityType.DocumentaryUnit.toString, "bad"))) must_== Some(
            Right(SearchParams(entities = Seq(EntityType.DocumentaryUnit))))
    }
  }
}
