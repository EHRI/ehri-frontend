
import java.io.File
import java.sql.CallableStatement
import auth.AccountManager
import auth.sql.SqlAccountManager

import scala.concurrent.Await
import scala.concurrent.duration._


package object helpers {

  import play.api.db.{Database, Databases}
  import play.api.db.evolutions._

  def testDatabase = Databases.inMemory(
    urlOptions = Map(
      "MODE" -> "MYSQL"
    ),
    config = Map(
      "logStatements" -> true
    )
  )

  def withDatabase[T](block: Database => T): T = {
    implicit val db = testDatabase
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

  def withFixtures[T](block: Database => T)(implicit app: play.api.Application): T = {
    withDatabase { implicit db =>
      loadSqlFixtures(db, app)
      block(db)
    }
  }

  /**
   * Load database fixtures.
   */
  def loadSqlFixtures(implicit db: Database, app: play.api.Application) = {
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
  def loadSqlResource(resource: String)(implicit db: Database) = db.withConnection { conn =>
    val file = new File(getClass.getClassLoader.getResource(resource).toURI)
    val path = file.getAbsolutePath
    val statement: CallableStatement = conn.prepareCall(s"RUNSCRIPT FROM '$path'")
    statement.execute()
    conn.commit()
  }
}
