package backend

import models.CypherQuery

import scala.concurrent.{ExecutionContext, Future}

/**
 * Data access object trait for creating,
 */
trait CypherQueryDAO {
  def get(id: String)(implicit executionContext: ExecutionContext): Future[CypherQuery]
  def create(cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String]
  def update(id: String, cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String]
  def list(params: (String,String)*)(implicit executionContext: ExecutionContext): Future[Seq[CypherQuery]]
  def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean]
}
