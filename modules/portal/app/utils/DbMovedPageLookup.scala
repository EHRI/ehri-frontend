package utils

import anorm.{NamedParameter, BatchSql}
import controllers._
import org.apache.commons.codec.digest.DigestUtils
import play.api.db.DB
import play.api.libs.concurrent.Akka
import play.api.mvc.Call
import views.html.errors.itemNotFound

import scala.concurrent.Future._
import scala.concurrent.{ExecutionContext, Future}

case class DbMovedPageLookup()(implicit app: play.api.Application) extends MovedPageLookup {

  implicit def executionContext: ExecutionContext =
    Akka.system.dispatchers.lookup("contexts.simple-db-looksups")

  override def hasMovedTo(path: String): Future[Option[String]] = Future {
    import anorm.SqlStringInterpolation
    import anorm.SqlParser.scalar
    val pathHash = DigestUtils.sha1Hex(path)
    DB.withConnection { implicit conn =>
      val newPath: Option[String] = SQL"SELECT new_path FROM moved_pages WHERE original_path_sha1 = $pathHash"
        .as(scalar[String].singleOpt)
      newPath
    }
  }(executionContext)

  override def addMoved(moved: Seq[(String, String)]): Future[Int] = Future {
    // NB: Due to a very annoying character limit imposed by the ancient
    // version of MySql on which we are forced to run, the lookup key here
    // is a hash of the original path, rather than the path itself (which could
    // easily overflow the 255 varchar limit on MySql 5.0.-something-old.)
    DB.withConnection { implicit conn =>
      val batch = BatchSql(
        anorm.SQL(
          """INSERT INTO moved_pages(original_path_sha1, original_path, new_path)
                  VALUES({hash}, {original}, {path})
                  ON DUPLICATE KEY UPDATE new_path = {path}"""
        ),
        moved.map { case (from, to) =>
          Seq[NamedParameter](
            'hash -> DigestUtils.sha1Hex(from),
            'original -> from,
            'path -> to
          )
        }
      )
      val rows: Array[Int] = batch.execute()
      rows.count(_ > 0)
    }
  }(executionContext)
}
