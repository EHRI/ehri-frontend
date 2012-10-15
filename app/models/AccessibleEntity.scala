package models

import play.api.libs.json.JsValue

class AccessibleEntity(
  override val id: Long,
  override val data: Map[String, JsValue],
  override val relationships: Map[String, List[Entity]]
) extends Entity(id, data, relationships) {
  private val adminKeys = List("isA", "identifier", "_desc")
  def identifier = property("identifier").map(_.as[String]).getOrElse(sys.error("No 'identifier' property found."))
  def entityType = EntityTypes.withName(
		  property("isA")
		  	.map(_.as[String])
		  	.getOrElse(sys.error("No 'identifier' property found.")))
		  	
  def valueData: Map[String, JsValue] = {
    data.filterNot { case (k, v) => adminKeys.contains(k) }
  }
}