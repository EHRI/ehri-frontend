package models

import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.util._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import defines.EntityType

object Entity {
  def fromString(s: String, t: EntityType.Value) = {
    new Entity(-1L, Map("identifier" -> JsString(s), "isA" -> JsString(t.toString)), Map())
  }
}

case class Entity(
  id: Long,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) {

  def property(name: String) = data.get(name)
  def stringProperty(name: String) = property(name).flatMap(_.asOpt[String]).getOrElse("")
  def relations(s: String): List[Entity] = relationships.getOrElse(s, List())  
  def withProperty(name: String, value: JsValue) = copy(data=data + (name -> value))
  def withRelation(s: String, r: Entity) = {
    val list: List[Entity] = relationships.getOrElse(s, Nil)
    copy(relationships=relationships + (s -> (list ++ List(r))))
  }
  private val adminKeys = List("isA", "identifier", "_desc")
  def valueData: Map[String, JsValue] = {
    data.filterNot { case (k, v) => adminKeys.contains(k) }
  }

  lazy val isA: EntityType.Value = EntityType.withName(
		  property("isA")
		  	.map(_.as[String])
		  	.getOrElse(sys.error("No 'isA' property found.")))
  
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
      map[List[Entity]](list(entityReads))))(Entity.apply _)
}
