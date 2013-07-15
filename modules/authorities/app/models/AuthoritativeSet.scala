package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"

  implicit object Converter extends RestConvertable[AuthoritativeSetF] with ClientConvertable[AuthoritativeSetF] {
    lazy val restFormat = models.json.AuthoritativeSetFormat.restFormat
    lazy val clientFormat = Json.format[AuthoritativeSetF]
  }
}

case class AuthoritativeSetF(
  isA: EntityType.Value = EntityType.AuthoritativeSet,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String]
) extends Model with Persistable


object AuthoritativeSet {
  implicit object Converter extends ClientConvertable[AuthoritativeSet] with RestReadable[AuthoritativeSet] {
    val restReads = models.json.AuthoritativeSetFormat.metaReads

    val clientFormat: Format[AuthoritativeSet] = (
      __.format[AuthoritativeSetF](AuthoritativeSetF.Converter.clientFormat) and
      nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
    )(AuthoritativeSet.apply _, unlift(AuthoritativeSet.unapply _))
  }
}


case class AuthoritativeSet(
  model: AuthoritativeSetF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent]
) extends AnyModel
  with MetaModel[AuthoritativeSetF]
  with Accessible {

  override def toStringLang(implicit lang: Lang): String = model.name.getOrElse(id)
}