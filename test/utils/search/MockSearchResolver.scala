package utils.search

import scala.concurrent.Future
import backend.{Readable, ApiUser}
import backend.rest.SearchDAO

/**
 * Resolves search results to DB items by doing a string
 * ID lookup.
 *
 * User: michaelb
 */
case class MockSearchResolver()(implicit val app: play.api.Application) extends SearchDAO with SearchItemResolver {
  def resolve[MT](results: Seq[SearchHit])(implicit apiUser: ApiUser, rd: Readable[MT]): Future[Seq[MT]] = {
    list(results.map(_.itemId))
  }
}
