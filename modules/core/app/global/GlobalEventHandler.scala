package global

import javax.inject.Inject

import backend.EventHandler
import play.api.Logger
import utils.search.SearchIndexMediator

import scala.concurrent.Future

case class GlobalEventHandler @Inject()(searchIndexer: SearchIndexMediator) extends EventHandler {
  // Bind the EntityDAO Create/Update/Delete actions
  // to the SolrIndexer update/delete handlers. Do this
  // asynchronously and log any failures...
  import java.util.concurrent.TimeUnit
  import scala.concurrent.duration.Duration
  import play.api.libs.concurrent.Execution.Implicits._

  def logFailure(id: String, func: String => Future[Unit]): Unit = {
    func(id) onFailure {
      case t => Logger.logger.error("Indexing error: " + t.getMessage)
    }
  }

  def handleCreate(id: String) = logFailure(id, searchIndexer.handle.indexId)
  def handleUpdate(id: String) = logFailure(id, searchIndexer.handle.indexId)

  // Special case - block when deleting because otherwise we get ItemNotFounds
  // after redirects because the item is still in the search index but not in
  // the database.
  def handleDelete(id: String) = logFailure(id, id => Future.successful[Unit] {
    concurrent.Await.result(searchIndexer.handle.clearId(id), Duration(1, TimeUnit.MINUTES))
  })
}
