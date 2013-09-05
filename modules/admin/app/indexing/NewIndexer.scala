package indexing

import defines.EntityType
import scala.concurrent.Future

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
  def indexId(id: String): Future[Option[Error]]

  /**
   * Index all items of a given type
   * @param entityType
   * @return
   */
  def indexType(entityType: EntityType.Value): Future[Stream[String]]

  /**
   * Index all children of a given item.
   * @param entityType
   * @param id
   * @return
   */
  def indexChildren(entityType: EntityType.Value, id: String): Future[Stream[String]]

  /**
   * Clear the index of all items.
   * @return
   */
  def clearAll: Future[Option[Error]]

  /**
   * Clear the index of all items of a given type.
   * @param entityType
   * @return
   */
  def clearType(entityType: EntityType.Value): Future[Option[Error]]

  /**
   * Clear a given item from the index.
   * @param id
   * @return
   */
  def clearId(id: String): Future[Option[Error]]
}
