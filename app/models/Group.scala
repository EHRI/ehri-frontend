package models

import models.base._
import defines.EntityType
import play.api.libs.json.{JsObject, Format, Json}
import models.json.{RestReadable, ClientConvertable, RestConvertable}

object GroupF {

  final val BELONGS_REL = "belongsTo"

  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val groupFormat: Format[GroupF] = json.GroupFormat.restFormat

  implicit object Converter extends RestConvertable[GroupF] with ClientConvertable[GroupF] {
    lazy val restFormat = models.json.rest.groupFormat
    lazy val clientFormat = models.json.client.groupFormat
  }
}

case class GroupF(
  isA: EntityType.Value = EntityType.Group,
  id: Option[String],
  identifier: String,
  name: String,
  description: Option[String] = None
) extends Persistable


case class Group(val e: Entity) extends NamedEntity with AccessibleEntity with Accessor with Formable[GroupF] {
  lazy val formable: GroupF = Json.toJson(e).as[GroupF]
  lazy val formableOpt: Option[GroupF] = Json.toJson(e).asOpt[GroupF]
}

object GroupMeta {
  implicit object Converter extends ClientConvertable[GroupMeta] with RestReadable[GroupMeta] {
    val restReads = models.json.GroupFormat.metaReads
    val clientFormat = models.json.client.groupMetaFormat
  }
}


case class GroupMeta(
  model: GroupF,
  groups: List[GroupMeta] = Nil,
  latestEvent: Option[SystemEventMeta] = None
) extends MetaModel[GroupF]

