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

trait HierarchicalEntity extends AccessibleEntity {

  val hierarchyRelationName: String

  def inheritedProperty(el: List[Entity], name: String): Option[(JsValue, Entity)] = {
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

  def inheritedProperty(name: String): Option[InheritedProperty] = {
    inheritedProperty(List(e), name) match {
      case Some((s, eo)) if e.id == eo.id => Some(InheritedProperty(s, None))
      case Some((s, eo)) => Some(InheritedProperty(s, Some(eo)))
      case _ => None
    }
  }
}