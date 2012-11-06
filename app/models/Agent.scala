package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines._


object Agent extends ManagedEntityBuilder[Agent] {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"  
  
  def apply(e: AccessibleEntity) = {
    new Agent(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse(""),      
      publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt),
      descriptions = e.relations(DESC_REL).map(AgentDescription.apply(_))
    )
  }

  // This will be required for basic form construction
  //def apply(id: Option[Long], identifier: String, name: String, publicationStatus: Option[PublicationStatus.Value],
  //		  	descriptions: List[DocumentaryUnitDescription])
  //		= new Agent(id, identifier, name, publicationStatus)

  // Special form unapply method
  def unform(g: Agent) = Some((g.id, g.identifier, g.name, g.publicationStatus, g.descriptions))  
}


case class Agent (
  val id: Option[Long],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  
  @Annotations.Relation(Agent.DESC_REL)
  val descriptions: List[AgentDescription] = Nil
  
  //@Annotations.Relation(Agent.ADDRESS_REL)
  //val addresses: List[Address] = Nil
  
) extends ManagedEntity {
  val isA = EntityType.Agent
}


object AgentDescription {
  def apply(e: Entity) = {
    new AgentDescription(
      id = Some(e.id),
      languageCode = e.property("languageCode").map(_.as[String]).getOrElse(""),
      name = e.property("name").flatMap(_.asOpt[String]),
      otherFormsOfName = e.property("otherFormsOfName").flatMap(_.asOpt[List[String]]).getOrElse(List()),
      parallelFormsOfName = e.property("parallelFormsOfName").flatMap(_.asOpt[List[String]]).getOrElse(List()),
      generalContext = e.property("generalContext").flatMap(_.asOpt[String])
    )
  }
}

case class AgentDescription(
  val id: Option[Long],
  val languageCode: String,
  val name: Option[String] = None,
  val otherFormsOfName: List[String] = Nil,
  val parallelFormsOfName: List[String] = Nil,  
  val generalContext: Option[String] = None
)  extends BaseModel {
  val isA = EntityType.AgentDescription
  
}

