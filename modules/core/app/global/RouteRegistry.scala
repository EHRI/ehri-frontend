package global

import play.api.mvc.Call
import models.base.AnyModel
import defines.EntityType

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class RouteRegistry(urls: Map[EntityType.Value, String => Call], default: Call, login: Call) {
  def urlFor(a: AnyModel): Call = urlFor(a.isA, a.id)
  def urlFor(t: EntityType.Value, id: String): Call = urls.get(t).map(t => t(id)).getOrElse(default)
  def optionalUrlFor(a: AnyModel): Option[Call] = optionalUrlFor(a.isA, a.id)
  def optionalUrlFor(t: EntityType.Value, id: String): Option[Call] = urls.get(t).map(f => f.apply(id))
}