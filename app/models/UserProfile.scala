package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future

object UserProfile extends ManagedEntityBuilder[UserProfile] {
  
  final val BELONGS_REL = "belongsTo"
  
  def apply(e: AccessibleEntity) = {
    new UserProfile(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse(""), // FIXME: Is this a good idea?
      groups = e.relations(BELONGS_REL).map(e => Group.apply(new AccessibleEntity(e)))
    )
  }
  
  def apply(id: Option[Long], identifier: String, name: String)
  		= new UserProfile(id, identifier, name, Nil)

  // Special form unapply method
  def unform(up: UserProfile) = Some((up.id, up.identifier, up.name))
}


case class UserProfile (
  val id: Option[Long],
  val identifier: String,
  val name: String,

  @Annotations.Relation(UserProfile.BELONGS_REL)
  val groups: List[Group] = Nil
) extends ManagedEntity with Accessor[Group] {
  val isA = EntityTypes.UserProfile

}
