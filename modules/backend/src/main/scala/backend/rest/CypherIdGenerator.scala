package backend.rest

import backend.IdGenerator
import backend.rest.cypher.Cypher
import com.google.inject.Singleton
import scala.concurrent.{Future, ExecutionContext}
import defines.EntityType
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology

import javax.inject.Inject

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
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
    val allIds = """MATCH (n:_Item) WHERE n.__type = {isA} RETURN n.identifier"""
    var params = Map("isA" -> JsString(entityType))
    cypher.cypher(allIds, params).map(id => nextId(id, pattern))
  }

  def getNextChildNumericIdentifier(parentId: String, entityType: EntityType.Value, pattern: String)(implicit executionContent: ExecutionContext): Future[String] = {
    val allIds =
      s"""
        | MATCH (c:_Item)-[:${Ontology.HAS_PERMISSION_SCOPE}]->(n:_Item)
        | WHERE n.__id = {id} AND c.__type = {isA}
        | RETURN c.identifier
        | """.stripMargin

    var params = Map(
      "isA" -> JsString(entityType),
      "id" -> JsString(parentId)
    )
    cypher.cypher(allIds, params).map(id => nextId(id, pattern))
  }
}
