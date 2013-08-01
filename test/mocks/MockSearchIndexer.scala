package mocks

import defines.EntityType
import models.UserProfile
import scala.concurrent.Future
import rest.RestError
import utils.search._
import play.api.libs.json.JsObject
import models.base.AnyModel

case class MockIndexerResponse() extends IndexerResponse

/**
 * User: michaelb
 *
 * TODO: Integrate better with fixtures.
 *
 */
case class MockSearchIndexer() extends Indexer {
  def commit: Future[IndexerResponse] = Future.successful(MockIndexerResponse())

  def deleteAll(commit: Boolean): Future[IndexerResponse] = Future.successful(MockIndexerResponse())

  def deleteItemsById(items: Stream[String], commit: Boolean): Future[IndexerResponse] = Future.successful(MockIndexerResponse())

  def deleteItemsByType(entityType: EntityType.Value, commit: Boolean): Future[IndexerResponse] = Future.successful(MockIndexerResponse())

  def deleteItems(items: Stream[AnyModel], commit: Boolean): Future[IndexerResponse] = Future.successful(MockIndexerResponse())

  def updateItem(item: JsObject, commit: Boolean): Future[IndexerResponse] = Future.successful(MockIndexerResponse())

  def updateItems(items: Stream[JsObject], commit: Boolean): Future[List[IndexerResponse]] = Future.successful(List(MockIndexerResponse()))
}
