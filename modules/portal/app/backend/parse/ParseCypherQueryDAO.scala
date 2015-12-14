package backend.parse

import javax.inject.Inject

import backend.{CypherQueryDAO, FeedbackDAO}
import models.{CypherQuery, Feedback}
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

/**
 * Parse implementation of a pre-baked cypher query data access object.
 */
case class ParseCypherQueryDAO @Inject()(
  implicit cache: CacheApi,
  config: play.api.Configuration,
  ws: WSClient
) extends ParseDAO[CypherQuery]("cypherqueries") with CypherQueryDAO
