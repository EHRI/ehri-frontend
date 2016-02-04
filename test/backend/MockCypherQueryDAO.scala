package backend

import backend.rest.ItemNotFound
import models.CypherQuery
import org.joda.time.DateTime

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.Future.{successful => immediate, failed}


case class MockCypherQueryDAO(buffer: collection.mutable.HashMap[Int, CypherQuery]) extends CypherQueryDAO{
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

  override def list(params: (String, String)*)(implicit executionContext: ExecutionContext): Future[Seq[CypherQuery]] =
    immediate(buffer.values.toSeq)

  override def create(cypherQuery: CypherQuery)(implicit executionContext: ExecutionContext): Future[String] = {
    val key = buffer.size + 1
    buffer += key -> cypherQuery
    immediate(key.toString)
  }
}
