package services.search

import models.EntityType
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import play.api.{Configuration, Logger}
import services.data.EventForwarder.{Create, Delete, Update}
import services.data.EventHandler

import javax.inject.{Inject, Named}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class IndexingEventHandler @Inject()(
  searchIndexer: SearchIndexMediator,
  config: Configuration,
  @Named("event-forwarder") forwarder: ActorRef
)(implicit ec: ExecutionContext, mat: Materializer) extends EventHandler {

  private val logger = Logger(getClass)

  // Bind the data API Create/Update/Delete actions
  // to the SearchIndexMediator update/delete handlers. Do this
  // asynchronously and log any failures...

  import scala.concurrent.duration.Duration

  private lazy val threshold = config.get[Int]("ehri.admin.bulkOperations.threshold")
  private def timeoutMinutes(items: Int): Duration = {
    if (items < threshold) 1.minute else config.get[Duration]("ehri.admin.bulkOperations.timeout")
  }

  private def logFailure(items: Seq[(EntityType.Value, String)], func: Seq[(EntityType.Value, String)] => Future[Unit]): Unit = func(items).failed.foreach {
    t => logger.error(s"Indexing error", t)
  }

  def handleCreate(items: (EntityType.Value, String)*): Unit = {
    forwarder ! Create(items)
    logFailure(items, items => searchIndexer.handle.indexIds(items.map(_._2): _*))
  }

  def handleUpdate(items: (EntityType.Value, String)*): Unit = {
    forwarder ! Update(items)
    logFailure(items, items => searchIndexer.handle.indexIds(items.map(_._2): _*))
  }

  // Special case - block when deleting because otherwise we get ItemNotFounds
  // after redirects because the item is still in the search index but not in
  // the database.
  def handleDelete(items: (EntityType.Value, String)*): Unit = logFailure(items, items => Future.successful[Unit] {
    val deletes = items.toSeq.grouped(threshold * 2).map { group =>
      val groupIds = group.map(_._2)
      logger.debug(s"Deleting ID group: ${groupIds.mkString(", ")}")
      forwarder ! Delete(group)
      searchIndexer.handle.clearIds(groupIds: _*)
    }
    // This runs all deletes in a synchronous sequence, as opposed to in parallel
    val allDone: Future[Done] = Source.fromIterator(() => deletes)
      .mapAsync(parallelism = 1)(identity)
      .runForeach(identity)

    concurrent.Await.result(allDone, timeoutMinutes(items.size))
  })
}
