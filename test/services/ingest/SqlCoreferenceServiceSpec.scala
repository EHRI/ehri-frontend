package services.ingest

import org.apache.pekko.actor.ActorSystem
import helpers._
import org.specs2.specification.AfterAll
import play.api.db.Database
import play.api.test.PlaySpecification

class SqlCoreferenceServiceSpec extends PlaySpecification with AfterAll {

  private val actorSystem = ActorSystem()
  override def afterAll(): Unit = await(actorSystem.terminate())

  def service(implicit db: Database) = SqlCoreferenceService(db, actorSystem)

  "coreference service should" should {
    "get coreferences" in withDatabaseFixture("coreference-fixtures.sql") { implicit db =>
      await(service.get("r1")) must_== Seq(
        Coreference("Person 1", "a1", "auths"),
        Coreference("Person 2", "a2", "auths")
      )
    }

    "save coreferences" in withDatabaseFixture("coreference-fixtures.sql") { implicit db =>
      await(service.get("r2")).size must_== 0
      val refs = Seq(Coreference("Foo", "c1", "s1"), Coreference("Bar", "c2", "s2"))
      await(service.save("r2", refs))
      await(service.get("r2")) must_== refs
    }

    "ignore duplicates" in withDatabaseFixture("coreference-fixtures.sql") { implicit db =>
      val refs = Seq(Coreference("Foo", "c1", "s1"), Coreference("Bar", "c2", "s2"))
      await(service.save("r2", refs))
      await(service.save("r2", refs))
      await(service.get("r2")) must_== refs
    }

    "delete coreferences" in withDatabaseFixture("coreference-fixtures.sql") { implicit db =>
      val auths = Seq(
        Coreference("Person 1", "a1", "auths"),
        Coreference("Person 2", "a2", "auths"))
      await(service.delete("r1", auths)) must_== 2
    }
  }
}
