package global

import javax.inject.Inject

import services.EventHandler
import play.api.Logger
import utils.search.SearchIndexMediator

import scala.concurrent.{ExecutionContext, Future}

case class GlobalEventHandler @Inject()(searchIndexer: SearchIndexMediator)(implicit executionContext: ExecutionContext) extends EventHandler {

  private val logger = Logger(getClass)

  // Bind the data API Create/Update/Delete actions
  // to the SearchIndexMediator update/delete handlers. Do this
  // asynchronously and log any failures...
  import java.util.concurrent.TimeUnit
  import scala.concurrent.duration.Duration

  def logFailure(id: String, func: String => Future[Unit]): Unit = {
    func(id) onFailure {
      case t => logger.error(s"Indexing error", t)
    }
  }

  def handleCreate(id: String): Unit = logFailure(id, id => searchIndexer.handle.indexIds(id))
  def handleUpdate(id: String): Unit = logFailure(id, id => searchIndexer.handle.indexIds(id))

  // Special case - block when deleting because otherwise we get ItemNotFounds
  // after redirects because the item is still in the search index but not in
  // the database.
  def handleDelete(id: String): Unit = logFailure(id, id => Future.successful[Unit] {
    concurrent.Await.result(searchIndexer.handle.clearIds(id), Duration(1, TimeUnit.MINUTES))
  })
}
