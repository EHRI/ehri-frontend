package services.cypher

import helpers._
import models.CypherQuery
import play.api.Application
import utils.{Page, PageParams}

class SqlCypherQueryServiceSpec extends IntegrationTestRunner {

  def queryService(implicit app: Application) = app.injector.instanceOf[SqlCypherQueryService]

  "Cypher Query service" should {
    "locate items correctly" in new DBTestApp("cypher-query-fixtures.sql") {
      val q = await(queryService.get("zq5xiWjYF6"))
      q.name must_== "Query 1"
    }

    "list items correctly" in new DBTestApp("cypher-query-fixtures.sql") {
      val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty))
      queries.total must_== 3
      queries.items.map(_.objectId) must_== Seq(Some("zq5xiWjYF6"), Some("nmGh0t9oq2"), Some("RE3Ye4TBqP"))
    }

    "list items correctly with filters" in new DBTestApp("cypher-query-fixtures.sql") {
      val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty, extra = Map("q" -> "doc")))
      queries.total must_== 1
      queries.items.map(_.objectId) must_== Seq(Some("RE3Ye4TBqP"))
    }

    "list only public/non-public items" in new DBTestApp("cypher-query-fixtures.sql") {
      val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty, extra = Map("public" -> "false")))
      queries.total must_== 1
      queries.items.map(_.objectId) must_== Seq(Some("zq5xiWjYF6"))
    }

    "list items correctly with name sort" in {
      new DBTestApp("cypher-query-fixtures.sql") {
        val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty, extra = Map("sort" -> "name")))
        queries.total must_== 3
        queries.items.map(_.objectId) must_== Seq(Some("zq5xiWjYF6"), Some("RE3Ye4TBqP"), Some("nmGh0t9oq2"))
      }
    }

    "delete items correctly" in new DBTestApp("cypher-query-fixtures.sql") {
      await(queryService.delete("zq5xiWjYF6")) must_== true
      await(queryService.list(PageParams.empty)).total must_== 2
    }

    "create items correctly" in new DBTestApp("cypher-query-fixtures.sql") {
      val f = CypherQuery(name = "Test", query = "RETURN 1")
      val id = await(queryService.create(f))
      // If we delete it successfully assume all good...
      await(queryService.delete(id)) must_== true
    }

    "update items correctly" in new DBTestApp("cypher-query-fixtures.sql") {
      val q = await(queryService.get("zq5xiWjYF6"))
      q.name must_== "Query 1"
      await(queryService.update("zq5xiWjYF6", q.copy(name = "Test")))
      await(queryService.get("zq5xiWjYF6")).name must_== "Test"
    }
  }
}
