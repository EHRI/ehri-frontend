package models

import defines._
import play.api.libs.json._
import models.base.AnyModel


object Entity {

  val ID = "id"
  val IDENTIFIER = "identifier"
  val ISA = "isA"
  val DATA = "data"
  val TYPE = "type"
  val RELATIONSHIPS = "relationships"

  def fromString(s: String, t: EntityType.Value) = {
    new Entity(s, t, Map(IDENTIFIER -> JsString(s)), Map())
  }
}

case class Entity(
  id: String,
  `type`: EntityType.Value,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) extends AnyModel{

  def property(name: String) = data.get(name)

  /**
   * Shortcut for fetching a Option[String] property.
   */
  def stringProperty(name: String): Option[String] = property(name).flatMap(_.asOpt[String])
  def listProperty(name: String): Option[List[String]] = property(name).flatMap(v => v.asOpt[List[String]] orElse v.asOpt[String].map(List(_)) )
  def relations(s: String): List[Entity] = relationships.getOrElse(s, List())
  def withProperty(name: String, value: JsValue) = copy(data=data + (name -> value))
  def withRelation(s: String, r: Entity) = {
    val list: List[Entity] = relationships.getOrElse(s, Nil)
    copy(relationships=relationships + (s -> (list ++ List(r))))
  }
  private val adminKeys = List(Entity.IDENTIFIER)
  def valueData: Map[String, JsValue] = {
    data.filterNot { case (k, v) => adminKeys.contains(k) }
  }

  lazy val isA: EntityType.Value = `type`
}