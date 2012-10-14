package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future

case class UserProfile(entity: Entity) {
	def id: Long = entity.id
	def identifier: Option[String] = entity.property("identifier").flatMap(_.asOpt[String])
	def name: Option[String] = entity.property("name").map(_.toString)
	def groups: Seq[Entity] = entity.relationships.getOrElse("belongsTo", Seq())
}
