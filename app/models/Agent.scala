package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines.EntityType

object Agent extends ManagedEntityBuilder[Agent] {

  final val ADDRESS_REL = "hasAddress"  
  
  def apply(e: AccessibleEntity) = {
    new Agent(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse("")      
    )
  }

  // This will be required for basic form construction
  //def apply(id: Option[Long], identifier: String, name: String)
  //		= new Agent(id, identifier, name)

  // Special form unapply method
  def unform(g: Agent) = Some((g.id, g.identifier, g.name))  
}


case class Agent (
  val id: Option[Long],
  val identifier: String,
  val name: String
  
  //@Annotations.Relation(Agent.ADDRESS_REL)
  //val addresses: List[Address] = Nil
  
) extends ManagedEntity {
  val isA = EntityType.Agent
}
