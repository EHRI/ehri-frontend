package services.rest

import services.IdGenerator
import services.rest.cypher.Cypher
import scala.concurrent.{Future, ExecutionContext}
import defines.EntityType
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology
import javax.inject.{Inject, Singleton}

@Singleton
case class CypherIdGenerator @Inject ()(cypher: Cypher) extends IdGenerator {

  private def nextId(idList: JsValue, pattern: String): String = {
    val result = idList.as[Map[String,JsValue]]
    val data: JsValue = result.getOrElse("data", Json.arr())
    val id = IdGenerator.nextNumericId(data.as[Seq[Seq[String]]].flatten)
    pattern.format(id)
  }

  def getNextNumericIdentifier(entityType: EntityType.Value, pattern: String)(implicit executionContent:
  ExecutionContext): Future[String] = {
    val allIds = """MATCH (n:_Entity) WHERE n.__type = {isA} RETURN n.identifier"""
    val params = Map("isA" -> JsString(entityType.toString))
    cypher.cypher(allIds, params).map(id => nextId(id, pattern))
  }

  def getNextChildNumericIdentifier(parentId: String, entityType: EntityType.Value, pattern: String)(implicit executionContent: ExecutionContext): Future[String] = {
    val allIds =
      s"""
        | MATCH (c:_Entity)-[:${Ontology.HAS_PERMISSION_SCOPE}]->(n:_Entity)
        | WHERE n.__id = {id} AND c.__type = {isA}
        | RETURN c.identifier
        | """.stripMargin

    val params = Map(
      "isA" -> JsString(entityType.toString),
      "id" -> JsString(parentId)
    )
    cypher.cypher(allIds, params).map(id => nextId(id, pattern))
  }
}
