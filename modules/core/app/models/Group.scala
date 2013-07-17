package models

import models.base._
import defines.EntityType
import play.api.libs.json._
import models.json._
import play.api.i18n.Lang
import play.api.libs.functional.syntax._

object GroupF {

  val NAME = "name"
  val DESCRIPTION = "description"

  implicit object Converter extends RestConvertable[GroupF] with ClientConvertable[GroupF] {
    lazy val restFormat = models.json.GroupFormat.restFormat
    lazy val clientFormat = Json.format[GroupF]
  }
}

case class GroupF(
  isA: EntityType.Value = EntityType.Group,
  id: Option[String],
  identifier: String,
  name: String,
  description: Option[String] = None
) extends Model with Persistable


/*
case class Group(val e: Entity) extends NamedEntity with AccessibleEntity with Accessor with Formable[GroupF] {
  lazy val formable: GroupF = Json.toJson(e).as[GroupF]
  lazy val formableOpt: Option[GroupF] = Json.toJson(e).asOpt[GroupF]
}
*/

object Group {
  implicit object Converter extends ClientConvertable[Group] with RestReadable[Group] {
    val restReads = models.json.GroupFormat.metaReads

    val clientFormat: Format[Group] = (
      __.format[GroupF](GroupF.Converter.clientFormat) and
      lazyNullableListFormat(__ \ "groups")(clientFormat) and
      lazyNullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
    )(Group.apply _, unlift(Group.unapply _))
  }
}


case class Group(
  model: GroupF,
  groups: List[Group] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None
) extends MetaModel[GroupF]
  with Accessor
  with Accessible {

  override def toStringLang(implicit lang: Lang) = model.name
}

