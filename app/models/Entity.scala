package models

import play.api.libs.json.JsValue

case class Entity(
  id: Long,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()
) {
  
  def property(name: String) = data.get(name)
  
  override def toString() = "<%s (%d)>".format(property("identifier"), id)
}

object EntityReader {
  // import just Reads helpers in scope
  import play.api.libs.json._
  import play.api.libs.json.util._
  import play.api.libs.json.Reads._
  import play.api.data.validation.ValidationError

  implicit val entityReads: Reads[Entity] = (
    (__ \ "id").read[Long] and
    (__ \ "data").lazyRead(map[JsValue]) and
    (__ \ "relationships").lazyRead(
        map[List[Entity]](list(entityReads)))
  )(Entity)
}
