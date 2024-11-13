package services.cypher

import java.time.Instant

import models.CypherQuery
import services.data.ItemNotFound
import utils.{Page, PageParams}

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful => immediate}


case class MockCypherQueryService(buffer: collection.mutable.HashMap[Int, CypherQuery]) extends CypherQueryService{

  override def get(id: String): Future[CypherQuery] =
    buffer.get(id.toInt).map (q => immediate(q)).getOrElse(failed(ItemNotFound(key = Some(id))))

  override def update(id: String, cypherQuery: CypherQuery): Future[String] = {
    buffer += id.toInt -> cypherQuery
    immediate(Instant.now.toString)
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
