package models.base

import models.Entity
import play.api.libs.json.JsValue

object HierarchicalEntity {
  
  import play.api.libs.json._

  val CHILD_REL = "childOf"

}

case class InheritedProperty(val value: JsValue, val inheritedFrom: Option[Entity]) {
  override def toString = value.toString
  def isInherited = inheritedFrom.isDefined
}

trait HierarchicalEntity[+T] {
  self: AccessibleEntity =>

  val hierarchyRelationName: String

  /**
   * Fetch the value of a property, inheriting from parent items.
   *
   * @param name
   * @return
   */
  def inheritedProperty(name: String): Option[InheritedProperty] = {
    inheritedProperty(List(e), name) match {
      case Some((s, eo)) if e.id == eo.id => Some(InheritedProperty(s, None))
      case Some((s, eo)) => Some(InheritedProperty(s, Some(eo)))
      case _ => None
    }
  }

  /**
   * Fetch a list of ancestors for this entity.
   *
   * @return
   */
  def ancestors: List[Entity] = ancestorList(e)

  private def ancestorList(et: Entity): List[Entity] = parentOf(et).map { parent =>
    parent :: ancestorList(parent)
  }.getOrElse(Nil)

  private def parentOf(et: Entity): Option[Entity] = et.relations(hierarchyRelationName).headOption

  private def inheritedProperty(el: List[Entity], name: String): Option[(JsValue, Entity)] = {
    el match {
      case lst @ head :: rest => {
        head.property(name) match {
          case os @ Some(s) => Some((s, head))
          case None => inheritedProperty(head.relations(hierarchyRelationName), name) match {
            case os @ Some(s) => os
            case None => inheritedProperty(rest, name)
          }
        }
      }
      case Nil => None
    }
  }
}