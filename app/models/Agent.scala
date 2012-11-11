package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines._
import models.base.AccessibleEntity
import models.base.NamedEntity
import models.base.DescribedEntity
import models.base.Description

case class AgentRepr(val e: Entity) extends NamedEntity with AccessibleEntity with DescribedEntity {
  override def descriptions: List[AgentDescriptionRepr] = e.relations(DescribedEntity.DESCRIBES_REL).map(AgentDescriptionRepr(_))  
}

case class AgentDescriptionRepr(val e: Entity) extends AccessibleEntity with Description {
  
}


object Agent {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
}


case class Agent (
  val id: Option[Long],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(Agent.DESC_REL)
  val descriptions: List[AgentDescription] = Nil  
) extends BaseModel {
  val isA = EntityType.Agent
}


object AgentDescription {

}

case class AgentDescription(
  val id: Option[Long],
  val languageCode: String,
  val name: Option[String] = None,
  val otherFormsOfName: List[String] = Nil,
  val parallelFormsOfName: List[String] = Nil,  
  val generalContext: Option[String] = None
) extends BaseModel {
  val isA = EntityType.AgentDescription
}

