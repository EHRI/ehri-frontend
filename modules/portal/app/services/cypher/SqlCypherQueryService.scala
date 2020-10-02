package services.cypher

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import anorm.{Macro, RowParser, SqlParser, _}
import models.CypherQuery
import play.api.db.Database
import services.data.ItemNotFound
import utils.{Page, PageParams}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlCypherQueryService @Inject()(db: Database, actorSystem: ActorSystem) extends CypherQueryService{

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val queryParser: RowParser[CypherQuery] =
    Macro.parser[CypherQuery]("id", "user_id", "name", "query", "description", "public", "created", "updated")

  override def get(id: String): Future[CypherQuery] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM cypher_queries WHERE id = $id"
        .as(queryParser.singleOpt).getOrElse(throw new ItemNotFound(id))
    }
  }

  override def update(id: String, query: CypherQuery): Future[String] =
    Future {
    db.withConnection { implicit conn =>
      SQL"""UPDATE cypher_queries
        SET
          name = ${query.name},
          query = ${query.query},
          description = ${query.description},
          public = ${query.public},
          updated = NOW()
        WHERE id = $id
      """.executeUpdate()
      id
    }
  }

  override def delete(id: String): Future[Boolean] = Future {
    db.withConnection { implicit  conn =>
      SQL"DELETE FROM cypher_queries WHERE id = $id".executeUpdate() == 1
    }
  }

  override def list(params: PageParams, extra: Map[String, String]): Future[Page[CypherQuery]] = Future {
    db.withTransaction { implicit conn =>
      val q = extra.get("q").filter(_.trim.nonEmpty).map(q => s"%${q.trim.replaceAll("\\s+", "%")}%")
      val public = extra.get("public").map(_ == true.toString)
      val order = extra.get("sort").filter(Seq("name", "created").contains)
      val items: List[CypherQuery] = SQL"""
        SELECT * FROM cypher_queries
          WHERE (coalesce($q, '') = '' OR (lower(name) LIKE $q OR lower(description) LIKE $q OR lower(query) LIKE $q))
          AND ($public IS NULL OR public = $public)
          ORDER BY
            CASE $order
              WHEN 'created' THEN to_char(created, 'YYYY-MM-DD HH24:MI:SS')
              WHEN 'name' THEN name
            END ASC,
            coalesce(updated, created) DESC
          LIMIT ${if (params.hasLimit) params.limit else Integer.MAX_VALUE}
          OFFSET ${params.offset}
        """.as(queryParser.*)
      val total: Int =
        SQL"""SELECT COUNT(id) FROM cypher_queries
          WHERE (coalesce($q, '') = '' OR (lower(name) LIKE $q OR lower(description) LIKE $q OR lower(query) LIKE $q))
          AND ($public IS NULL OR public = $public)
        """.as(SqlParser.scalar[Int].single)
      Page(items = items, total = total, offset = params.offset, limit = params.limit)
    }

  }

  override def create(data: CypherQuery): Future[String] = Future {
    val strId = utils.db.newObjectId(10)
    val query = data.copy(objectId = data.objectId.orElse(Some(strId)),
      created = data.created.orElse(Some(ZonedDateTime.now())))
    db.withConnection { implicit conn =>
      SQL"""INSERT INTO cypher_queries
        (id, user_id, name, query, description, public, created, updated)
        VALUES (
          ${query.objectId},
          ${query.userId},
          ${query.name},
          ${query.query},
          ${query.description},
          ${query.public},
          ${query.created},
          ${query.updated}
      )""".execute()
      strId
    }
  }
}
