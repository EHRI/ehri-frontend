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
case class CypherIdGenerator(idFormat: String = "%06d") extends IdGenerator with RestDAO {
  val cypher = new CypherDAO

  private val extractNums = "(\\d+)\\D*$".r

  private def safeInt(s : String) : Option[Int] = try {
    Some(s.toInt)
  } catch {
    case _ : java.lang.NumberFormatException => None
  }

  private def nextId(idList: JsValue): String = {
    val result = idList.as[Map[String,JsValue]]
    val data: JsValue = result.getOrElse("data", Json.arr())
    val id = nextNumericId(data.as[Seq[Seq[String]]].flatten)
    idFormat.format(id)
  }

  def nextNumericId(ids: Seq[String]): Int = ids.flatMap { rid =>
    rid.split("\\D+").filterNot(_ == "").headOption.flatMap(safeInt)
  }.padTo(1, 0).max + 1 // ensure we get '1' with an empty list

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
