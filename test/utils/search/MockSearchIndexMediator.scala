package utils.search

import defines.EntityType
import play.api.Logger
import scala.concurrent.Future

case class MockSearchIndexMediator(eventBuffer: collection.mutable.ListBuffer[String]) extends SearchIndexMediator {
  def handle = new MockSearchIndexMediatorHandle(eventBuffer)
}

/**
 * User: michaelb
 *
 * TODO: Integrate better with fixtures.
 */
case class MockSearchIndexMediatorHandle(eventBuffer: collection.mutable.ListBuffer[String]) extends SearchIndexMediatorHandle {

  def indexIds(ids: String*) = {
    ids.foreach(id => eventBuffer += id)
    Logger.logger.info("Indexing: " + ids)
    Future.successful(())
  }
  def indexTypes(entityTypes: Seq[EntityType.Value]) = {
    eventBuffer += entityTypes.toString
    Logger.logger.info("Indexing: " + entityTypes)
    Future.successful(())
  }
  def indexChildren(entityType: EntityType.Value, id: String) = {
    eventBuffer += id
    Logger.logger.info("Indexing children: " + entityType + " -> " + id)
    Future.successful(())
  }
  def clearAll() = {
    eventBuffer += "clear-all"
    Logger.logger.info("Clearing entire index...")
    Future.successful(())
  }
  def clearTypes(entityTypes: Seq[EntityType.Value]) = {
    eventBuffer += "clear-types:" + entityTypes.toString
    Logger.logger.info("Clearing entity types: " + entityTypes)
    Future.successful(())
  }
  def clearIds(ids: String*) = {
    ids.foreach(id => eventBuffer += id)
    Logger.logger.info("Clearing id: " + ids)
    Future.successful(())
  }
  def clearKeyValue(key: String, value: String) = {
    eventBuffer += "clear-key-value " + s"$key=$value"
    Logger.logger.info("Clearing key-value: " + s"$key=$value")
    Future.successful(())
  }
}
