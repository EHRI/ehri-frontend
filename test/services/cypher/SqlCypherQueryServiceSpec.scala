package services.cypher

import helpers._
import models.CypherQuery
import play.api.db.Database
import play.api.test.PlaySpecification
import utils.{Page, PageParams}

class SqlCypherQueryServiceSpec extends SimpleAppTest with PlaySpecification {

  def queryService(implicit db: Database) = SqlCypherQueryService(db, implicitApp.actorSystem)

  "Cypher Query service" should {
    "locate items correctly" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      val q = await(queryService.get("zq5xiWjYF6"))
      q.name must_== "Query 1"
    }

    "list items correctly" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty))
      queries.total must_== 3
      queries.items.map(_.objectId) must_== Seq(Some("zq5xiWjYF6"), Some("nmGh0t9oq2"), Some("RE3Ye4TBqP"))
    }

    "list items correctly with filters" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty, extra = Map("q" -> "doc")))
      queries.total must_== 1
      queries.items.map(_.objectId) must_== Seq(Some("RE3Ye4TBqP"))
    }

    "list only public/non-public items" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty, extra = Map("public" -> "false")))
      queries.total must_== 1
      queries.items.map(_.objectId) must_== Seq(Some("zq5xiWjYF6"))
    }

    "list items correctly with name sort" in {
      withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
        val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty, extra = Map("sort" -> "name")))
        queries.total must_== 3
        queries.items.map(_.objectId) must_== Seq(Some("zq5xiWjYF6"), Some("RE3Ye4TBqP"), Some("nmGh0t9oq2"))
      }
    }

    "delete items correctly" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      await(queryService.delete("zq5xiWjYF6")) must_== true
      await(queryService.list(PageParams.empty)).total must_== 2
    }

    "create items correctly" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      val f = CypherQuery(name = "Test", query = "RETURN 1")
      val id = await(queryService.create(f))
      // If we delete it successfully assume all good...
      await(queryService.delete(id)) must_== true
    }

    "update items correctly" in withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
      val q = await(queryService.get("zq5xiWjYF6"))
      q.name must_== "Query 1"
      await(queryService.update("zq5xiWjYF6", q.copy(name = "Test")))
      await(queryService.get("zq5xiWjYF6")).name must_== "Test"
    }
  }
}
