package services.data

import anorm.{BatchSql, Macro, NamedParameter, RowParser, SqlStringInterpolation}
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlApplicationEventService @Inject()(db: Database, config: Configuration)(implicit actorSystem: ActorSystem) extends ApplicationEventService {

  private implicit def executionContext: ExecutionContext = actorSystem.dispatchers.lookup("contexts.db-writes")

  private implicit val parser: RowParser[ApplicationEvent] = Macro.parser[ApplicationEvent]("id", "data", "name")

  override def save(events: Seq[ApplicationEvent]): Future[Int] = Future {
    db.withTransaction { implicit conn =>
      val q =
        """INSERT INTO application_event (id, data, name)
           VALUES ({id}::UUID, {data}, {name})
           ON CONFLICT DO NOTHING
           """

      if (events.nonEmpty) {
        val inserts = events.map { r =>
          Seq[NamedParameter](
            "id" -> r.id,
            "data" -> r.data,
            "name" -> r.name,
          )
        }

        BatchSql(q, inserts.head, inserts.tail: _*).execute().sum
      } else 0
    }
  }

  override def get(lastInsertId: String): Future[Seq[ApplicationEvent]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
           SELECT *
           FROM application_event
           WHERE id::varchar >= $lastInsertId
             AND EXISTS (SELECT 1 FROM application_event WHERE id::varchar = $lastInsertId)
           ORDER BY id ASC""".as(parser.*)
    }
  }
}
