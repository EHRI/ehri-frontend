package utils.search

import javax.inject._

import play.api.cache.CacheApi
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import backend.{Readable, ApiUser}
import backend.rest.SearchDAO

@Singleton
case class MockSearchResolver @Inject()(implicit cache: CacheApi, app: play.api.Application, ws: WSClient) extends SearchDAO with SearchItemResolver {
  def resolve[MT](results: Seq[SearchHit])(implicit apiUser: ApiUser, rd: Readable[MT]): Future[Seq[MT]] = {
    list(results.map(_.itemId))
  }
}
