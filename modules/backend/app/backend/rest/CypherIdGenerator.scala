package backend.rest

import backend.IdGenerator
import backend.rest.cypher.CypherDAO
import scala.concurrent.{Future, ExecutionContext}
import defines.EntityType
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class CypherIdGenerator(idFormat: String = "%06d")(implicit val app: play.api.Application)
  extends IdGenerator with RestDAO {
  val cypher = new CypherDAO

  private def nextId(idList: JsValue): String = {
    val result = idList.as[Map[String,JsValue]]
    val data: JsValue = result.getOrElse("data", Json.arr())
    val id = IdGenerator.nextNumericId(data.as[Seq[Seq[String]]].flatten)
    idFormat.format(id)
  }

  def getNextNumericIdentifier(entityType: EntityType.Value)(implicit executionContent: ExecutionContext): Future[String] = {
    val allIds = """START n = node:entities(__ISA__ = {isA}) RETURN n.identifier"""
    var params = Map("isA" -> JsString(entityType))
    cypher.cypher(allIds, params).map(nextId)
  }

  def getNextChildNumericIdentifier(parentId: String, entityType: EntityType.Value)(implicit executionContent: ExecutionContext): Future[String] = {
    val allIds =
      s"""
        | START n = node:entities(__ID__ = {id})
        | MATCH c-[:${Ontology.HAS_PERMISSION_SCOPE}]->n
        | WHERE c.__ISA__ = {isA}
        | RETURN c.identifier
        | """.stripMargin

    var params = Map(
      "isA" -> JsString(entityType),
      "id" -> JsString(parentId)
    )
    cypher.cypher(allIds, params).map(nextId)
  }
}
