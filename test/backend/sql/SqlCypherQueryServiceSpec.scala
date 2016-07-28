package backend.sql

import akka.actor.ActorSystem
import helpers._
import models.CypherQuery
import play.api.db.Database
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification
import utils.{Page, PageParams}

class SqlCypherQueryServiceSpec extends PlaySpecification {

  implicit val actorSystem = new GuiceApplicationBuilder().build().injector.instanceOf[ActorSystem]

  def queryService(implicit db: Database) = new SqlCypherQueryService()(db, actorSystem)

  "Cypher Query service" should {
    "locate items correctly" in {
      withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
        val q = await(queryService.get("zq5xiWjYF6"))
        q.name must_== "Query 1"
      }
    }

    "list items correctly" in {
      withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
        val queries: Page[CypherQuery] = await(queryService.list(PageParams.empty))
        queries.total must_== 3
        queries.items.map(_.objectId) must_== Seq(Some("nmGh0t9oq2"), Some("RE3Ye4TBqP"), Some("zq5xiWjYF6"))
      }
    }

    "delete items correctly" in {
      withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
        await(queryService.delete("zq5xiWjYF6")) must_== true
        await(queryService.list(PageParams.empty)).total must_== 2
      }
    }

    "create items correctly" in {
      withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
        val f = CypherQuery(name = "Test", query = "RETURN 1")
        val id = await(queryService.create(f))
        // If we delete it successfully assume all good...
        await(queryService.delete(id)) must_== true
      }
    }

    "update items correctly" in {
      withDatabaseFixture("cypher-query-fixtures.sql") { implicit db =>
        val q = await(queryService.get("zq5xiWjYF6"))
        q.name must_== "Query 1"
        await(queryService.update("zq5xiWjYF6", q.copy(name = "Test")))
        await(queryService.get("zq5xiWjYF6")).name must_== "Test"
      }
    }
  }
}
