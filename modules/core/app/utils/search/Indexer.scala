package utils.search

import defines.EntityType
import scala.concurrent.Future
import scala.sys.process.ProcessLogger
import play.api.libs.iteratee.Concurrent

case class IndexingError(msg: String) extends Exception(msg)

/**
 * User: www.github.com/mikesname
 *
 * Interface to an indexer service.
 *
 */
trait Indexer {

  def withChannel(channel: Concurrent.Channel[String], formatter: String => String = identity[String]): Indexer = this

  /**
   * Index a single item by id
   * @param id
   * @return
   */
  def indexId(id: String): Future[Unit]

  /**
   * Index all items of a given set of types.
   * @param entityTypes
   * @return
   */
  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit]

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Unit]

  /**
   * Clear the index of all items.
   * @return
   */
  def clearAll(): Future[Unit]

  /**
   * Clear the index of all items of a given type.
   * @param entityTypes
   * @return
   */
  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit]

  /**
   * Clear a given item from the index.
   * @param id
   * @return
   */
  def clearId(id: String): Future[Unit]

  /**
   * Clear a given item from the index.
   * @param key
   * @param value
   * @return
   */
  def clearKeyValue(key: String, value: String): Future[Unit]
}
