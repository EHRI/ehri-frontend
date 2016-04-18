package backend

import models.CypherQuery
import utils.{Page, PageParams}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Data access object trait for managing canned
 * Cypher queries.
 */
trait CypherQueryService {
  def get(id: String)(implicit executionContext: ExecutionContext): Future[CypherQuery]
  def create(cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String]
  def update(id: String, cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String]
  def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty)(
        implicit executionContext: ExecutionContext): Future[Page[CypherQuery]]
  def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean]
}
