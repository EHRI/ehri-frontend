package backend

import backend.rest.ItemNotFound
import models.CypherQuery
import org.joda.time.DateTime
import utils.{Page, PageParams}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.Future.{successful => immediate, failed}


case class MockCypherQueryService(buffer: collection.mutable.HashMap[Int, CypherQuery]) extends CypherQueryService{
  override def get(id: String)(implicit executionContext: ExecutionContext): Future[CypherQuery] =
    buffer.get(id.toInt).map (q => immediate(q)).getOrElse(failed(ItemNotFound(key = Some(id))))

  override def update(id: String, cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String] = {
    buffer += id.toInt -> cypherQuery
    immediate(DateTime.now.toString)
  }

  override def delete(id: String)(implicit executionContext: ExecutionContext): Future[Boolean] = {
    buffer -= id.toInt
    immediate(true)
  }

  override def list(pageParams: PageParams = PageParams.empty, params: Map[String, String] = Map.empty)(implicit executionContext: ExecutionContext): Future[Page[CypherQuery]] = immediate(Page(items = buffer.values.toSeq))

  override def create(cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> cypherQuery
    immediate(key.toString)
  }
}
