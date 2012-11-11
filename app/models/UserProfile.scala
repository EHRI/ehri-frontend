package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines.EntityType

object UserProfile extends ManagedEntityBuilder[UserProfile] {
  
  final val BELONGS_REL = "belongsTo"
  final val PLACEHOLDER_TITLE = "[No Title Found]"
  
  /**
   * Deserialization constructor.
   */
  def apply(e: AccessibleEntity) = {
    new UserProfile(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse(PLACEHOLDER_TITLE), // FIXME: Is this a good idea?
      location = e.property("location").flatMap(_.asOpt[String]),
      about = e.property("about").flatMap(_.asOpt[String]),
      languages = e.property("languages").flatMap(_.asOpt[List[String]]).getOrElse(List()),
      groups = e.relations(BELONGS_REL).map(e => Group.apply(new AccessibleEntity(e)))
    )
  }

  /**
   * Minimal constructor.
   */
  def apply(id: Option[Long], identifier: String, name: String, groups: List[Group])
  		= new UserProfile(id, identifier, name, None, None, Nil, groups)
  /**
   * Form constructor.
   */
  def apply(id: Option[Long], identifier: String, name: String, location: Option[String],
		  	about: Option[String], languages: List[String])
  		= new UserProfile(id, identifier, name, location, about, languages, Nil)

  /**
   * Form destructuring.
   */
  def unform(up: UserProfile) = Some((up.id, up.identifier, up.name, up.location, up.about, up.languages))
}


case class UserProfile (
  val id: Option[Long],
  val identifier: String,
  val name: String,
  val location: Option[String],
  val about: Option[String],
  val languages: List[String] = Nil,

  @Annotations.Relation(UserProfile.BELONGS_REL)
  val groups: List[Group] = Nil
) extends ManagedEntity with Accessor {
  val isA = EntityType.UserProfile
  
  def isAdmin = getAccessor(groups, "admin").isDefined

}
