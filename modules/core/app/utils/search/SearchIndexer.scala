package utils.search

import defines.EntityType
import play.api.libs.iteratee.Concurrent

import scala.concurrent.Future

case class IndexingError(msg: String) extends Exception(msg)

/**
 * Interface to an indexer service.
 *
 */
trait SearchIndexer {

  def withChannel(channel: Concurrent.Channel[String], formatter: String => String = identity[String]): SearchIndexer = this

  /**
   * Index a single item by id.
   *
   * @param id the id of the item
   */
  def indexId(id: String): Future[Unit]

  /**
   * Index all items of a given set of types.
   *
   * @param entityTypes types to index
   */
  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit]

  /**
   * Index all children of a given item.
   *
   * @param entityType the types to clear
   * @param id the id of the parent
   */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Unit]

  /**
   * Clear the index of all items.
   */
  def clearAll(): Future[Unit]

  /**
   * Clear the index of all items of a given type.
   *
   * @param entityTypes the types to clear
   */
  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit]

  /**
   * Clear a given item from the index.
   *
   * @param id the id of the item to delete
   */
  def clearId(id: String): Future[Unit]

  /**
   * Clear a given item from the index.
   *
   * @param key the item key
   * @param value the item value
   */
  def clearKeyValue(key: String, value: String): Future[Unit]
}
