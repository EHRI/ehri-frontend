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
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}


object AuthoritativeSetMeta {
  implicit object Converter extends ClientConvertable[AuthoritativeSetMeta] with RestReadable[AuthoritativeSetMeta] {
    val restReads = models.json.AuthoritativeSetFormat.metaReads

    val clientFormat: Format[AuthoritativeSetMeta] = (
      __.format[AuthoritativeSetF](AuthoritativeSetF.Converter.clientFormat) and
      nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
    )(AuthoritativeSetMeta.apply _, unlift(AuthoritativeSetMeta.unapply _))
  }
}


case class AuthoritativeSetMeta(
  model: AuthoritativeSetF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta]
) extends AnyModel
  with MetaModel[AuthoritativeSetF]
  with Accessible {

  override def toStringLang(implicit lang: Lang): String = model.name.getOrElse(id)
}