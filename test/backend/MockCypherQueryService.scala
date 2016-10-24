package backend

import backend.rest.ItemNotFound
import models.CypherQuery
import java.time.LocalDateTime
import utils.{Page, PageParams}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate, failed}


case class MockCypherQueryService(buffer: collection.mutable.HashMap[Int, CypherQuery]) extends CypherQueryService{

  override def get(id: String): Future[CypherQuery] =
    buffer.get(id.toInt).map (q => immediate(q)).getOrElse(failed(ItemNotFound(key = Some(id))))

  override def update(id: String, cypherQuery: CypherQuery): Future[String] = {
    buffer += id.toInt -> cypherQuery
    immediate(LocalDateTime.now.toString)
  }

  override def delete(id: String): Future[Boolean] = {
    buffer -= id.toInt
    immediate(true)
  }

  override def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty): Future[Page[CypherQuery]] = immediate(Page(items = buffer.values.toSeq))

  override def create(cypherQuery: CypherQuery): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> cypherQuery
    immediate(key.toString)
  }
}
