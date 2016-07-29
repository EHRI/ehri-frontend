package backend.sql

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import backend.CypherQueryService
import models.CypherQuery
import org.joda.time.DateTime
import play.api.db.Database
import anorm.JodaParameterMetaData._
import anorm._
import utils.{Page, PageParams}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class SqlCypherQueryService @Inject()(
  implicit db: Database, actorSystem: ActorSystem
) extends CypherQueryService{

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val queryParser: RowParser[CypherQuery] =
    Macro.parser[CypherQuery]("id", "user_id", "name", "query", "description", "public", "created", "updated")

  override def get(id: String): Future[CypherQuery] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM cypher_queries WHERE id = $id".as(queryParser.single)
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
      val items: List[CypherQuery] = SQL"""
        SELECT * FROM cypher_queries
          ORDER BY created DESC
          LIMIT ${if (params.hasLimit) params.limit else Integer.MAX_VALUE}
          OFFSET ${params.offset}
        """.as(queryParser.*)
      val total: Int = SQL"SELECT COUNT(id) FROM cypher_queries".as(SqlParser.scalar[Int].single)
      Page(items = items, total = total, offset = params.offset, limit = params.limit)
    }

  }

  override def create(data: CypherQuery): Future[String] = Future {
    val query = data.copy(objectId = data.objectId.orElse(Some(utils.db.newObjectId(10))),
      createdAt = data.createdAt.orElse(Some(DateTime.now())))
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
          ${query.createdAt},
          ${query.updatedAt}
      )""".execute()
      query.objectId.get
    }
  }
}
