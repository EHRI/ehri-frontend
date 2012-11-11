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

case class GroupRepr(val e: Entity) extends NamedEntity with AccessibleEntity with Accessor {
  
}

object Group {

  final val BELONGS_REL = "belongsTo"    
}


case class Group (
  val id: Option[Long],
  val identifier: String,
  val name: String
) extends BaseModel {
  val isA = EntityType.Group
}
