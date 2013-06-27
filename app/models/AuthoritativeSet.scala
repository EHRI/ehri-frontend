package models

import base._

import models.base.Persistable
import defines.EntityType
import models.json.{RestReadable, ClientConvertable, RestConvertable}


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"

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


object AuthoritativeSetMeta {
  implicit object Converter extends ClientConvertable[AuthoritativeSetMeta] with RestReadable[AuthoritativeSetMeta] {
    val restReads = models.json.AuthoritativeSetFormat.metaReads
    val clientFormat = models.json.client.authoritativeSetMetaFormat
  }
}


case class AuthoritativeSetMeta(
  model: AuthoritativeSetF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta]
) extends AnyModel
  with MetaModel[AuthoritativeSetF]
  with Accessible