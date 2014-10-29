package backend

import defines._
import play.api.libs.json._
import play.api.libs.functional.syntax._


object Entity {

  val ID = "id"
  val IDENTIFIER = "identifier"
  val ISA = "isA"
  val DATA = "data"
  val TYPE = "type"
  val RELATIONSHIPS = "relationships"
  val META = "meta"
  val CHILD_COUNT = "childCount"

  /**
   * Reads a generic entity.
   */
  implicit val entityReads: Reads[Entity] = (
    (__ \ Entity.ID).read[String] and
      (__ \ Entity.TYPE).read[EntityType.Type](defines.EnumUtils.enumReads(EntityType)) and
      (__ \ Entity.DATA).lazyRead(Reads.map[JsValue]) and
      (__ \ Entity.RELATIONSHIPS).lazyRead(Reads.map[List[Entity]](Reads.list(entityReads)))
    )(Entity.apply _)

  /**
   * Writes a generic entity.
   */
  implicit val entityWrites: Writes[Entity] = (
    (__ \ Entity.ID).write[String] and
      (__ \ Entity.TYPE).write[EntityType.Type](defines.EnumUtils.enumWrites) and
      (__ \ Entity.DATA).lazyWrite(Writes.map[JsValue]) and
      (__ \ Entity.RELATIONSHIPS).lazyWrite(Writes.map[List[Entity]])
    )(unlift(Entity.unapply))

  /**
   * Format for a generic entity.
   */
  val entityFormat: Format[Entity] = Format(entityReads, entityWrites)
}

case class Entity(
  id: String,
  `type`: EntityType.Value,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) {

  lazy val isA: EntityType.Value = `type`
}