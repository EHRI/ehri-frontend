package utils.search

import scala.concurrent.Future
import utils.search._
import backend.ApiUser
import models.json.RestReadable
import backend.rest.SearchDAO

/**
 * Resolves search results to DB items by doing a string
 * ID lookup.
 *
 * User: michaelb
 */
case class MockSearchResolver() extends SearchDAO with Resolver {
  def resolve[MT](results: Seq[SearchHit])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[List[MT]] = {
    list(results.map(_.itemId))
  }
}
