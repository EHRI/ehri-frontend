package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.{JsObject, Format, Json}
import defines.EnumUtils.enumWrites
import models.json.{RestReadable, ClientConvertable, RestConvertable}


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val authoritativeSetFormat: Format[AuthoritativeSetF] = json.AuthoritativeSetFormat.restFormat

  implicit object Converter extends RestConvertable[AuthoritativeSetF] with ClientConvertable[AuthoritativeSetF] {
    lazy val restFormat = models.json.rest.authoritativeSetFormat
    lazy val clientFormat = models.json.client.authoritativeSetFormat
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

case class AuthoritativeSet(e: Entity)
  extends NamedEntity
  with AnnotatableEntity
  with Formable[AuthoritativeSetF] {

  lazy val formable: AuthoritativeSetF = Json.toJson(e).as[AuthoritativeSetF]
  lazy val formableOpt: Option[AuthoritativeSetF] = Json.toJson(e).asOpt[AuthoritativeSetF]
}


object AuthoritativeSetMeta {
  implicit object Converter extends ClientConvertable[AuthoritativeSetMeta] with RestReadable[AuthoritativeSetMeta] {
    val restReads = models.json.AuthoritativeSetFormat.metaReads
    val clientFormat = models.json.client.authoritativeSetMetaFormat
  }
}


case class AuthoritativeSetMeta(
  model: AuthoritativeSetF,
  latestEvent: Option[SystemEventMeta]
) extends MetaModel[AuthoritativeSetF]