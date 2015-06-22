package models

import java.sql.{Connection, SQLException}
import play.api.db.Database
import scala.util.{Failure, Try}

package object sql {

  // FIXME: This is a hack to match the integrity errors thrown by H2 and MySQL. It won't
  // work with PostgreSQL.
  // It also won't tell you which field on which the error occurred.
  private val integrityMatch = """.*(primary key violation|Duplicate entry).*""".r

  def withIntegrityCheck[T](f: => Connection => T)(implicit db: Database): Try[T] = Try {
    db.withConnection { connection =>
      f(connection)
    }
  } recoverWith {
    case e: SQLException if integrityMatch.findFirstIn(e.getMessage).isDefined =>
      Failure(IntegrityError(e))
  }
}
