package services.data

import anorm.{BatchSql, Macro, NamedParameter, RowParser, SqlStringInterpolation}
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlEventStore @Inject()(db: Database, config: Configuration)(implicit actorSystem: ActorSystem) extends EventStore {

  private implicit def executionContext: ExecutionContext = actorSystem.dispatchers.lookup("contexts.db-writes")

  private implicit val parser: RowParser[StoredEvent] = Macro.parser[StoredEvent]("data", "id", "name")

  override def store(events: Seq[StoredEvent]): Future[Int] = Future {
    db.withTransaction { implicit conn =>
      val q =
        """INSERT INTO lifecycle_event (data, id, name)
           VALUES ({data}, {id}::UUID, {name})
           ON CONFLICT DO NOTHING
           """

      if (events.nonEmpty) {
        val inserts = events.map { r =>
          Seq[NamedParameter](
            "data" -> r.data,
            "id" -> r.id,
            "name" -> r.name,
          )
        }

        BatchSql(q, inserts.head, inserts.tail: _*).execute().sum
      } else 0
    }
  }

  override def get(lastInsertId: String): Future[Seq[StoredEvent]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
           SELECT *
           FROM lifecycle_event
           WHERE id::varchar >= $lastInsertId
             AND EXISTS (SELECT 1 FROM lifecycle_event WHERE id::varchar = $lastInsertId)
           ORDER BY id ASC""".as(parser.*)
    }
  }
}
