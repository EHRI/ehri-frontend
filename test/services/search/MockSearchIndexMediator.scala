package services.search

import defines.EntityType
import play.api.Logger

import scala.concurrent.Future

case class MockSearchIndexMediator(eventBuffer: collection.mutable.ListBuffer[String]) extends SearchIndexMediator {
  def handle = MockSearchIndexMediatorHandle(eventBuffer)
}

case class MockSearchIndexMediatorHandle(eventBuffer: collection.mutable.ListBuffer[String])
  extends SearchIndexMediatorHandle {

  private val logger = Logger(classOf[MockSearchIndexMediatorHandle])

  def indexIds(ids: String*): Future[Unit] = {
    ids.foreach(id => eventBuffer += id)
    logger.debug(s"Indexing: $ids")
    Future.successful(())
  }

  def indexTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = {
    eventBuffer += entityTypes.toString
    logger.debug(s"Indexing: $entityTypes")
    Future.successful(())
  }

  def indexChildren(entityType: EntityType.Value, id: String): Future[Unit] = {
    eventBuffer += id
    logger.debug(s"Indexing children: $entityType -> $id")
    Future.successful(())
  }

  def clearAll(): Future[Unit] = {
    eventBuffer += "clear-all"
    logger.debug("Clearing entire index...")
    Future.successful(())
  }

  def clearTypes(entityTypes: Seq[EntityType.Value]): Future[Unit] = {
    eventBuffer += "clear-types:" + entityTypes.toString
    logger.debug(s"Clearing entity types: $entityTypes")
    Future.successful(())
  }

  def clearIds(ids: String*): Future[Unit] = {
    ids.foreach(id => eventBuffer += id)
    logger.debug(s"Clearing id: $ids")
    Future.successful(())
  }

  def clearKeyValue(key: String, value: String): Future[Unit] = {
    eventBuffer += "clear-key-value " + s"$key=$value"
    logger.debug(s"Clearing key-value: $key=$value")
    Future.successful(())
  }
}
