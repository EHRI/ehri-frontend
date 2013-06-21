package models

import models.base._
import defines.EntityType
import play.api.libs.json.{Format, Json}
import models.json.Convertable

object GroupF {

  final val BELONGS_REL = "belongsTo"

  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val groupFormat: Format[GroupF] = json.GroupFormat.restFormat

  implicit object Converter extends Convertable[GroupF] {
    lazy val restFormat = models.json.GroupFormat.restFormat
  }
}

case class GroupF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val description: Option[String] = None
) extends Persistable {
  val isA = EntityType.Group
}


case class Group(val e: Entity) extends NamedEntity with AccessibleEntity with Accessor with Formable[GroupF] {
  lazy val formable: GroupF = Json.toJson(e).as[GroupF]
  lazy val formableOpt: Option[GroupF] = Json.toJson(e).asOpt[GroupF]
}

