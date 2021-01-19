package services.search

import services.data
import services.data.{ApiUser, DataApi}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
  * Resolve search hits to DB items by the GID field
  */
case class GidSearchResolver @Inject()(dataApi: DataApi)(implicit executionContext: ExecutionContext) extends SearchItemResolver {
  def resolve[MT: data.Readable](docs: Seq[SearchHit])(implicit apiUser: ApiUser): Future[Seq[Option[MT]]] =
    dataApi.withContext(apiUser).fetch(gids = docs.map(_.gid))
}
