package models

import defines.EntityType

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.Reads.JsValueReads
import play.api.libs.json.Reads.LongReads
import play.api.libs.json.Reads.StringReads
import play.api.libs.json.Reads.functorReads
import play.api.libs.json.Reads.list
import play.api.libs.json.Reads.map
import play.api.libs.json.Writes
import play.api.libs.json.Writes.JsValueWrites
import play.api.libs.json.Writes.LongWrites
import play.api.libs.json.Writes.mapWrites
import play.api.libs.json.Writes.traversableWrites
import play.api.libs.json.__
import play.api.libs.json.util.functionalCanBuildApplicative
import play.api.libs.json.util.toFunctionalBuilderOps
import play.api.libs.json.util.unlift

object Entity {
  
  val ID = "id"
  val ISA = "isA"
  val IDENTIFIER = "identifier"
  val DATA = "data"
  val RELATIONSHIPS = "relationships"  
  
  def fromString(s: String, t: EntityType.Value) = {
    new Entity(-1L, Map(IDENTIFIER -> JsString(s), ISA -> JsString(t.toString)), Map())
  }
}

case class Entity(
  id: Long,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) {

  def property(name: String) = data.get(name)
  
  /**
   * Shortcut for fetching a Option[String] property.
   */
  def stringProperty(name: String) = property(name).flatMap(_.asOpt[String])
  def relations(s: String): List[Entity] = relationships.getOrElse(s, List())  
  def withProperty(name: String, value: JsValue) = copy(data=data + (name -> value))
  def withRelation(s: String, r: Entity) = {
    val list: List[Entity] = relationships.getOrElse(s, Nil)
    copy(relationships=relationships + (s -> (list ++ List(r))))
  }
  private val adminKeys = List(Entity.ISA, Entity.IDENTIFIER, "_desc")
  def valueData: Map[String, JsValue] = {
    data.filterNot { case (k, v) => adminKeys.contains(k) }
  }

  lazy val isA: EntityType.Value = EntityType.withName(
		  property(Entity.ISA)
		  	.map(_.as[String])
		  	.getOrElse(sys.error("No 'isA' property found.")))
  
  override def toString() = "<%s (%d)>".format(property(Entity.IDENTIFIER), id)
}

object EntityWriter {
  implicit val entityWrites: Writes[Entity] = (
     (__ \ Entity.ID).write[Long] and
     (__ \ Entity.DATA).lazyWrite(mapWrites[JsValue]) and
     (__ \ Entity.RELATIONSHIPS).lazyWrite(
         mapWrites[List[Entity]])
  )(unlift(Entity.unapply))
}

object EntityReader {
  implicit val entityReads: Reads[Entity] = (
    (__ \ Entity.ID).read[Long] and
    (__ \ Entity.DATA).lazyRead(map[JsValue]) and
    (__ \ Entity.RELATIONSHIPS).lazyRead(
      map[List[Entity]](list(entityReads))))(Entity.apply _)
}
