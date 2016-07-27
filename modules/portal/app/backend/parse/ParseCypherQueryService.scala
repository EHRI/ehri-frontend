package backend.parse

import javax.inject.Inject

import backend.CypherQueryService
import models.CypherQuery
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

/**
 * Parse implementation of a pre-baked cypher query data access object.
 */
case class ParseCypherQueryService @Inject()(
  implicit val cache: CacheApi,
  val config: play.api.Configuration,
  val ws: WSClient,
  val executionContext: ExecutionContext
) extends ParseService[CypherQuery]("cypherqueries") with CypherQueryService
