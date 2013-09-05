package indexing

import defines.EntityType
import scala.concurrent.Future
import scala.sys.process.ProcessLogger

/**
 * User: www.github.com/mikesname
 *
 * Interface to an indexer service.
 *
 */
trait NewIndexer {

  /**
   * Index a single item by id
   * @param id
   * @return
   */
  def indexId(id: String): Future[Option[String]]

  /**
   * Index all items of a given set of types.
   * @param entityTypes
   * @return
   */
  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Stream[String]]

  /**
   * Index all items of a given set of types, logging output to
   * the supplied ProcessLogger.
   * @param entityTypes
   * @param progress
   * @return
   */
  def indexTypes(entityTypes: Seq[EntityType.Value], progress: String => Unit): Future[Stream[String]]

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Stream[String]]

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String, progress: String => Unit): Future[Stream[String]]

  /**
   * Clear the index of all items.
   * @return
   */
  def clearAll: Future[Option[String]]

  /**
   * Clear the index of all items of a given type.
   * @param entityTypes
   * @return
   */
  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Option[String]]

  /**
   * Clear a given item from the index.
   * @param id
   * @return
   */
  def clearId(id: String): Future[Option[String]]
}
