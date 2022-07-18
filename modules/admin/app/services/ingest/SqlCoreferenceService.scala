package services.ingest

import akka.actor.ActorSystem
import anorm.SqlParser._
import anorm._
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


case class SqlCoreferenceService @Inject()(db: Database, actorSystem: ActorSystem) extends CoreferenceService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.db-writes")

  private implicit val parser: RowParser[Coreference] = Macro.parser[Coreference]("text", "target_id", "set_id")

  override def get(id: String): Future[Seq[Coreference]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
           SELECT r.text, r.target_id, r.set_id
           FROM coreference_value r, coreference c
           WHERE r.coreference_id = c.id
             AND c.repo_id = $id
           ORDER BY r.set_id, r.target_id""".as(parser.*)
    }
  }

  override def save(repoId: String, refs: Seq[Coreference]): Future[Int] = Future {
    db.withTransaction { implicit conn =>
      val id: Int = SQL"""INSERT INTO coreference (repo_id, updated)
           VALUES ($repoId, NOW())
           ON CONFLICT (repo_id) DO UPDATE SET updated = NOW()
           RETURNING id""".as(scalar[Int].single)

      val q =
        """INSERT INTO coreference_value (coreference_id, text, target_id, set_id)
           VALUES ({coreference_id}, {text}, {target_id}, {set_id})
           ON CONFLICT DO NOTHING
           """

      if (refs.nonEmpty) {
        val inserts = refs.map { r =>
          Seq[NamedParameter](
            "coreference_id" -> id,
            "text" -> r.text,
            "target_id" -> r.targetId,
            "set_id" -> r.setId
          )
        }

        BatchSql(q, inserts.head, inserts.tail: _*).execute().sum
      } else 0
    }
  }

  override def delete(repoId: String, refs: Seq[Coreference]): Future[Int] = Future {
    db.withConnection { implicit conn =>
      val q = """DELETE FROM coreference_value r
            USING coreference c
            WHERE c.id = r.coreference_id
              AND c.repo_id = {repo_id}
              AND r.text = {text}
              AND r.target_id = {target_id}
              AND r.set_id = {set_id}"""
      if (refs.nonEmpty) {
        val deletes = refs.map { r =>
          Seq[NamedParameter](
            "text" -> r.text,
            "target_id" -> r.targetId,
            "set_id" -> r.setId,
            "repo_id" -> repoId
          )
        }

        BatchSql(q, deletes.head, deletes.tail: _*).execute().sum
      } else 0
    }
  }
}
