package backend

import models.CypherQuery
import utils.{Page, PageParams}

import scala.concurrent.Future

/**
 * Data access object trait for managing canned
 * Cypher queries.
 */
trait CypherQueryService {
  def get(id: String): Future[CypherQuery]
  def create(cypherQuery: CypherQuery): Future[String]
  def update(id: String, cypherQuery: CypherQuery): Future[String]
  def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty): Future[Page[CypherQuery]]
  def delete(id: String): Future[Boolean]
}
