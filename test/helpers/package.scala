
import java.io._

import akka.actor.ActorSystem
import auth.AccountManager
import auth.sql.SqlAccountManager
import models.OpenIDAssociation
import play.api.{Configuration, LoggerConfigurator}

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
    LoggerConfigurator(getClass.getClassLoader).foreach(_.configure(env))
    Databases.apply(
      config.getString("db.default.driver")
        .getOrElse(sys.error("Missing database config for driver")),
      config.getString("db.default.url")
        .getOrElse(sys.error("Missing database config for url")),
      config = Map(
        "username" -> config.getString("db.default.username").getOrElse(""),
        "password" -> config.getString("db.default.password").getOrElse("")
      )
    )
  }

  def withDatabase[T](block: Database => T): T = {
    val db: Database = loadDatabaseForSimpleConfig
    Evolutions.withEvolutions(db) {
      block(db)
    }
  }

  def withDatabaseFixture[T](resource: String)(block: Database => T): T = {
    withDatabase { implicit db =>
      loadSqlResource(resource)
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
    val accounts: AccountManager = SqlAccountManager()
    mockdata.users.foreach { case (profile, account) =>
      val acc = Await.result(accounts.create(account), 1.second)
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
