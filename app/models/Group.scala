package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future

object Group extends ManagedEntityBuilder[Group] {
  
  def apply(e: AccessibleEntity) = {
    new Group(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse("")
    )
  }
}


case class Group (
  val id: Option[Long],
  val identifier: String,
  val name: String
) extends ManagedEntity {
  val isA = EntityTypes.Group
}
