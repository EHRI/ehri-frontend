package global

import play.api.mvc.Call
import models.base.AnyModel
import defines.EntityType

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class RouteRegistry(urls: Map[EntityType.Value, String => Call], default: String => Call) {
  def urlFor(a: AnyModel): Call = urlFor(a.isA, a.id)
  def urlFor(t: EntityType.Value, id: String): Call = urls.getOrElse(t, default)(id)
  def optionalUrlFor(a: AnyModel): Option[Call] = optionalUrlFor(a.isA, a.id)
  def optionalUrlFor(t: EntityType.Value, id: String): Option[Call] = urls.get(t).map(f => f.apply(id))
}