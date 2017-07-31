package services.search

import defines.EntityType
import play.api.Logger

import scala.concurrent.Future

case class MockSearchIndexMediator(eventBuffer: collection.mutable.ListBuffer[String]) extends SearchIndexMediator {
  def handle = MockSearchIndexMediatorHandle(eventBuffer)
}

case class MockSearchIndexMediatorHandle(eventBuffer: collection.mutable.ListBuffer[String])
  extends SearchIndexMediatorHandle {

  def indexIds(ids: String*): Future[Unit] = {
    ids.foreach(id => eventBuffer += id)
    Logger.logger.info("Indexing: " + ids)
    Future.successful(())
  }

  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = {
    eventBuffer += entityTypes.toString
    Logger.logger.info("Indexing: " + entityTypes)
    Future.successful(())
  }

  def indexChildren(entityType: EntityType.Value, id: String): Future[Unit] = {
    eventBuffer += id
    Logger.logger.info("Indexing children: " + entityType + " -> " + id)
    Future.successful(())
  }

  def clearAll(): Future[Unit] = {
    eventBuffer += "clear-all"
    Logger.logger.info("Clearing entire index...")
    Future.successful(())
  }

  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = {
    eventBuffer += "clear-types:" + entityTypes.toString
    Logger.logger.info("Clearing entity types: " + entityTypes)
    Future.successful(())
  }

  def clearIds(ids: String*): Future[Unit] = {
    ids.foreach(id => eventBuffer += id)
    Logger.logger.info("Clearing id: " + ids)
    Future.successful(())
  }

  def clearKeyValue(key: String, value: String): Future[Unit] = {
    eventBuffer += "clear-key-value " + s"$key=$value"
    Logger.logger.info("Clearing key-value: " + s"$key=$value")
    Future.successful(())
  }
}
