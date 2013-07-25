package utils.search

import concurrent.Future
import defines.EntityType
import models.base.AnyModel
import play.api.libs.json.JsObject

/**
 * User: mikebryant
 */
trait Indexer {
  def commit: Future[IndexerResponse]
  def deleteAll(commit: Boolean = false): Future[IndexerResponse]
  def deleteItemsById(items: Stream[String], commit: Boolean = true): Future[IndexerResponse]
  def deleteItemsByType(entityType: EntityType.Value, commit: Boolean = true): Future[IndexerResponse]
  def deleteItems(items: Stream[AnyModel], commit: Boolean = true): Future[IndexerResponse]

  def updateItem(item: JsObject, commit: Boolean = true): Future[IndexerResponse]
  def updateItems(items: Stream[JsObject], commit: Boolean = true): Future[List[IndexerResponse]]

}

trait IndexerResponse {

}
