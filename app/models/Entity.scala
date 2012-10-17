package models

import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._

case class Entity(
  id: Long,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) {

  def property(name: String) = data.get(name)

  override def toString() = "<%s (%d)>".format(property("identifier"), id)
}

object EntityWriter {
  implicit val entityWrites: Writes[Entity] = (
     (__ \ "id").write[Long] and
     (__ \ "data").lazyWrite(mapWrites[JsValue]) and
     (__ \ "relationships").lazyWrite(
         mapWrites[List[Entity]])
  )(unlift(Entity.unapply))
}

object EntityReader {
  implicit val entityReads: Reads[Entity] = (
    (__ \ "id").read[Long] and
    (__ \ "data").lazyRead(map[JsValue]) and
    (__ \ "relationships").lazyRead(
      map[List[Entity]](list(entityReads))))(Entity)
}
