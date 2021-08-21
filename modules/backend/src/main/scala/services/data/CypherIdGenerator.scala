package services.data

import eu.ehri.project.definitions.Ontology
import models.EntityType

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import services.cypher.CypherService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class CypherIdGenerator @Inject ()(cypher: CypherService) extends IdGenerator {

  private def nextId(rows: Seq[List[JsValue]], pattern: String): String = {
    val allIds: Seq[String] = rows.collect { case JsString(id) :: _ => id }
    val intId = IdGenerator.nextNumericId(allIds)
    pattern.format(intId)
  }

  def getNextNumericIdentifier(entityType: EntityType.Value, pattern: String)(implicit executionContent:
  ExecutionContext): Future[String] = {
    val allIds = """MATCH (n:_Entity) WHERE n.__type = $isA RETURN n.identifier"""
    val params = Map("isA" -> JsString(entityType.toString))
    cypher.get(allIds, params).map(res => nextId(res.data, pattern))
  }

  def getNextChildNumericIdentifier(parentId: String, entityType: EntityType.Value, pattern: String)(implicit executionContent: ExecutionContext): Future[String] = {
    val allIds =
      s"""
        | MATCH (c:_Entity)-[:${Ontology.HAS_PERMISSION_SCOPE}]->(n:_Entity)
        | WHERE n.__id = $$id AND c.__type = $$isA
        | RETURN c.identifier
        | """.stripMargin

    val params = Map(
      "isA" -> JsString(entityType.toString),
      "id" -> JsString(parentId)
    )
    cypher.get(allIds, params).map(res => nextId(res.data, pattern))
  }
}
