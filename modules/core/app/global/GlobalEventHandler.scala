package global

import javax.inject.Inject

import backend.EventHandler
import play.api.Logger
import utils.search.SearchIndexMediator

import scala.concurrent.Future

case class GlobalEventHandler @Inject()(searchIndexer: SearchIndexMediator) extends EventHandler {
  // Bind the data API Create/Update/Delete actions
  // to the SearchIndexMediator update/delete handlers. Do this
  // asynchronously and log any failures...
  import java.util.concurrent.TimeUnit
  import scala.concurrent.duration.Duration
  import play.api.libs.concurrent.Execution.Implicits._

  def logFailure(id: String, func: String => Future[Unit]): Unit = {
    func(id) onFailure {
      case t => Logger.logger.error("Indexing error: " + t.getMessage)
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
