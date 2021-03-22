
import play.api.{Configuration, LoggerConfigurator}

import java.io._


package object helpers {

  import play.api.db.evolutions._
  import play.api.db.{Database, Databases}

  private def loadDatabaseForSimpleConfig: Database = {
    // This is annoying, but because we don't have (or want) a
    // whole app to test parts of the DB behaviour we have to load
    // the DB config manually (I think.)
    // NB: There should be an easier way of doing this.
    val env = play.api.Environment.simple()
    val config = Configuration.load(env)
    LoggerConfigurator(env.classLoader).foreach(_.configure(env))
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

  def withDatabaseFixture[T](db: Database, resources: String*)(block: Database => T): T = {
      implicit val _db: Database = db
      Evolutions.withEvolutions(_db) {
        resources.filter(_.nonEmpty).foreach(loadSqlResource)
        block(_db)
      }
  }

  def withDatabaseFixture[T](resources: String*)(block: Database => T): T = {
    withDatabase { implicit db =>
      resources.foreach(loadSqlResource)
      block(db)
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
