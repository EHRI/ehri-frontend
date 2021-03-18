
import java.io._

import akka.actor.ActorSystem
import models.OpenIDAssociation
import play.api.Configuration
import services.accounts.{AccountManager, SqlAccountManager}

import scala.concurrent.Await
import scala.concurrent.duration._


package object helpers {

  import play.api.db.{Database, Databases}
  import play.api.db.evolutions._

  private def loadDatabaseForSimpleConfig: Database = {
    // This is annoying, but because we don't have (or want) a
    // whole app to test parts of the DB behaviour we have to load
    // the DB config manually (I think.)
    // NB: There should be an easier way of doing this.
    val env = play.api.Environment.simple()
    val config = Configuration.load(env)
    Databases.apply(
      config.get[String]("db.default.driver"),
      config.get[String]("db.default.url"),
      config = Map(
        "username" -> config.getOptional[String]("db.default.username").getOrElse(""),
        "password" -> config.getOptional[String]("db.default.password").getOrElse("")
      )
    )
  }

  def withDatabase[T](block: Database => T): T = {
    val db: Database = loadDatabaseForSimpleConfig
    try {
      Evolutions.withEvolutions(db) {
        block(db)
      }
    } finally {
      db.shutdown()
    }
  }

  def withDatabaseFixture[T](resources: String*)(block: Database => T): T = {
    withDatabase { implicit db =>
      resources.foreach(loadSqlResource)
      block(db)
    }
  }

  def withFixtures[T](block: Database => T)(implicit actorSystem: ActorSystem): T = {
    withDatabase { implicit db =>
      loadSqlFixtures(db, actorSystem)
      block(db)
    }
  }

  /**
   * Load database fixtures.
   */
  def loadSqlFixtures(implicit db: Database, actorSystem: ActorSystem): List[Option[OpenIDAssociation]] = {
    val accounts: AccountManager = SqlAccountManager(db, actorSystem)
    mockdata.users.foreach { case (profile, account) =>
      Await.result(accounts.create(account), 1.second)
    }
    mockdata.oAuth2Associations.map { assoc =>
      Await.result(accounts.oAuth2.addAssociation(assoc.id, assoc.providerId, assoc.provider), 1.second)
    }
    mockdata.openIDAssociations.map { assoc =>
      Await.result(accounts.openId.addAssociation(assoc.id, assoc.url), 1.second)
    }
  }

  /**
   * Load a file containing SQL statements into the DB.
   */
  def loadSqlResource(resource: String)(implicit db: Database): Unit = db.withConnection { conn =>
    val file = new File(getClass.getClassLoader.getResource(resource).toURI)
    val path = file.getAbsolutePath
    val runner = new ScriptRunner(conn, false, true)
    runner.setLogWriter(new PrintWriter(new OutputStream {
      override def write(b: Int): Unit = ()
    }))
    runner.runScript(new BufferedReader(new FileReader(path)))
  }
}
