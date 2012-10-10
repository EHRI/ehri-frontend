package models

import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._


object EntityTypes extends Enumeration() {
  type Type = Value
  val UserProfile = Value("userProfile")
  val DocumentaryUnit = Value("documentaryUnit")
  val Agent = Value("agent")
  val Group = Value("group")
  val Annotation = Value("annotation")
}

case class Entity(
  id: Long,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) {

  def property(name: String) = data.get(name)

  override def toString() = "<%s (%d)>".format(property("identifier"), id)
}


object EntityReader {
  implicit val entityReads: Reads[Entity] = (
    (__ \ "id").read[Long] and
    (__ \ "data").lazyRead(map[JsValue]) and
    (__ \ "relationships").lazyRead(
      map[List[Entity]](list(entityReads))))(Entity)
}
