package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines.EntityType
import models.base.AccessibleEntity
import models.base.Accessor
import models.base.NamedEntity
import models.base.Formable

case class GroupRepr(val e: Entity) extends NamedEntity with AccessibleEntity with Accessor with Formable[Group] {

  def to: Group = new Group(
    id = Some(e.id),
    identifier = identifier,
    name = e.property("name").flatMap(_.asOpt[String]).getOrElse(UserProfile.PLACEHOLDER_TITLE)
  )
}

object Group {

  final val BELONGS_REL = "belongsTo"
}

case class Group(
  val id: Option[Long],
  val identifier: String,
  val name: String) extends BaseModel {
  val isA = EntityType.Group
}
