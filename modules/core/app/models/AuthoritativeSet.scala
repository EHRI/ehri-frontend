package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._


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
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(AuthoritativeSet.apply _, unlift(AuthoritativeSet.unapply _))
  }

  implicit object Resource extends RestResource[AuthoritativeSet] {
    val entityType = EntityType.AuthoritativeSet
  }

  import AuthoritativeSetF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.AuthoritativeSet),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText)
    )(AuthoritativeSetF.apply)(AuthoritativeSetF.unapply)
  )
}


case class AuthoritativeSet(
  model: AuthoritativeSetF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[AuthoritativeSetF]
  with Accessible
  with Holder[HistoricalAgent] {

  override def toStringLang(implicit lang: Lang): String = model.name.getOrElse(id)
}