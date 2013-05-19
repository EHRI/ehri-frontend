package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumUtils.enumWrites


object AuthoritativeSetF {

  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val authoritativeSetFormat = json.AuthoritativeSetFormat.authoritativeSetFormat
}

case class AuthoritativeSetF(
  val id: Option[String],
  val identifier: String,
  val name: Option[String],
  val description: Option[String]
) extends Persistable {
  val isA = EntityType.AuthoritativeSet

  def toJson = Json.toJson(this)
}


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
