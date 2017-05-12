package backend.rest

import javax.inject.Inject

import backend.{DataApi, ApiUser, Readable}
import utils.search.{SearchHit, SearchItemResolver}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Resolve search hits to DB items by the GID field
 */
case class GidSearchResolver @Inject()(dataApi: DataApi)(implicit executionContext: ExecutionContext) extends SearchItemResolver {
  def resolve[MT: Readable](docs: Seq[SearchHit])(implicit apiUser: ApiUser): Future[Seq[Option[MT]]] =
    dataApi.withContext(apiUser).fetch(gids = docs.map(_.gid))
}

/**
 * Resolve search hits to DB items by the itemId field
 */
case class IdSearchResolver @Inject()(dataApi: DataApi)(implicit executionContext: ExecutionContext) extends SearchItemResolver {
  def resolve[MT: Readable](docs: Seq[SearchHit])(implicit apiUser: ApiUser): Future[Seq[Option[MT]]] =
    dataApi.withContext(apiUser).fetch(ids = docs.map(_.itemId))
}
