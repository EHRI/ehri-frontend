package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines.EntityType

object Group extends ManagedEntityBuilder[Group] {

  final val BELONGS_REL = "belongsTo"  
  
  def apply(e: AccessibleEntity) = {
    new Group(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse(""),
      groups = e.relations(BELONGS_REL).map(e => Group.apply(new AccessibleEntity(e)))      
    )
  }
  
  def apply(id: Option[Long], identifier: String, name: String)
  		= new Group(id, identifier, name, Nil)

  // Special form unapply method
  def unform(g: Group) = Some((g.id, g.identifier, g.name))  
}


case class Group (
  val id: Option[Long],
  val identifier: String,
  val name: String,
  
  @Annotations.Relation(UserProfile.BELONGS_REL)
  val groups: List[Group] = Nil
  
) extends ManagedEntity with Accessor[Group] {
  val isA = EntityType.Group
}
