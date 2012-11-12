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

case class UserProfileRepr(val e: Entity) extends AccessibleEntity
	with Accessor with NamedEntity with Formable[UserProfile] {
   def isAdmin = getAccessor(groups, "admin").isDefined

  def to: UserProfile = new UserProfile(
    id = Some(e.id),
    identifier = identifier,
    name = e.property("name").flatMap(_.asOpt[String]).getOrElse(UserProfile.PLACEHOLDER_TITLE),
    location = e.property("location").flatMap(_.asOpt[String]),
	about = e.property("about").flatMap(_.asOpt[String]),	
	languages = e.property("languages").flatMap(_.asOpt[List[String]]).getOrElse(List())          
   )
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
) extends Persistable {
  val isA = EntityType.UserProfile
}
