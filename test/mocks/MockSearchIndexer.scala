package mocks

import defines.EntityType
import utils.search._
import play.api.Logger

/**
 * User: michaelb
 *
 * TODO: Integrate better with fixtures.
 *
 */
case class MockSearchIndexer() extends Indexer {
  val eventBuffer = collection.mutable.ArrayBuffer.empty[String]
  def indexId(id: String) {
    eventBuffer += id
    Logger.logger.info("Indexing: " + id)
  }
  def indexTypes(entityTypes: Seq[EntityType.Value]) {
    eventBuffer += entityTypes.toString
    Logger.logger.info("Indexing: " + entityTypes)
  }
  def indexChildren(entityType: EntityType.Value, id: String) {
    eventBuffer += id
    Logger.logger.info("Indexing children: " + entityType + " -> " + id)
  }
  def clearAll() {
    eventBuffer += "clear-all"
    Logger.logger.info("Clearing entire index...")
  }
  def clearTypes(entityTypes: Seq[EntityType.Value]) {
    eventBuffer += "clear-types:" + entityTypes.toString
    Logger.logger.info("Clearing entity types: " + entityTypes)
  }
  def clearId(id: String) {
    eventBuffer += id
    Logger.logger.info("Clearing id: " + id)
  }
}
