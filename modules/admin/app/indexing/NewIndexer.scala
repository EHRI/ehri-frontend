package indexing

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
trait NewIndexer {

  def withChannel(channel: Concurrent.Channel[String], formatter: String => String = identity[String]): NewIndexer = this

  /**
   * Index a single item by id
   * @param id
   * @return
   */
  def indexId(id: String): Unit

  /**
   * Index all items of a given set of types.
   * @param entityTypes
   * @return
   */
  def indexTypes(entityTypes: Seq[EntityType.Value]): Unit

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String): Unit

  /**
   * Clear the index of all items.
   * @return
   */
  def clearAll(): Unit

  /**
   * Clear the index of all items of a given type.
   * @param entityTypes
   * @return
   */
  def clearTypes(entityTypes: Seq[EntityType.Value]): Unit

  /**
   * Clear a given item from the index.
   * @param id
   * @return
   */
  def clearId(id: String): Unit
}
