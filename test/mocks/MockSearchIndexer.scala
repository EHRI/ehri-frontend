package mocks

import defines.EntityType
import utils.search._
import play.api.Logger
import scala.concurrent.Future

/**
 * User: michaelb
 *
 * TODO: Integrate better with fixtures.
 *
 */
case class MockSearchIndexer() extends Indexer {
  val eventBuffer = collection.mutable.ArrayBuffer.empty[String]
  def indexId(id: String) = Future.successful {
    eventBuffer += id
    Logger.logger.info("Indexing: " + id)
  }
  def indexTypes(entityTypes: Seq[EntityType.Value]) = Future.successful {
    eventBuffer += entityTypes.toString
    Logger.logger.info("Indexing: " + entityTypes)
  }
  def indexChildren(entityType: EntityType.Value, id: String) = Future.successful {
    eventBuffer += id
    Logger.logger.info("Indexing children: " + entityType + " -> " + id)
  }
  def clearAll() = Future.successful {
    eventBuffer += "clear-all"
    Logger.logger.info("Clearing entire index...")
  }
  def clearTypes(entityTypes: Seq[EntityType.Value]) = Future.successful {
    eventBuffer += "clear-types:" + entityTypes.toString
    Logger.logger.info("Clearing entity types: " + entityTypes)
  }
  def clearId(id: String) = Future.successful {
    eventBuffer += id
    Logger.logger.info("Clearing id: " + id)
  }
  def clearKeyValue(key: String, value: String) = Future.successful {
    eventBuffer += "clear-key-value " + s"$key=$value"
    Logger.logger.info("Clearing key-value: " + s"$key=$value")
  }
}
