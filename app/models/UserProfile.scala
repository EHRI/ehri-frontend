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

case class UserProfileRepr(val e: Entity) extends AccessibleEntity with Accessor with NamedEntity {
   def isAdmin = getAccessor(groups, "admin").isDefined	
   
}

object UserProfile {
  
  final val PLACEHOLDER_TITLE = "[No Title Found]"
}


case class UserProfile (
  val id: Option[Long],
  val identifier: String,
  val name: String,
  val location: Option[String],
  val about: Option[String],
  val languages: List[String] = Nil
) extends BaseModel {
  val isA = EntityType.UserProfile
}
