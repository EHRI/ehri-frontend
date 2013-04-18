package models

import models.base.Formable

import defines.EntityType
import play.api.libs.json.Json



object AccessPointF {
  val TYPE = "type"
  val TEXT = "text"
}

case class AccessPointF(
  val id: Option[String],
  val `type`: Option[EntityType.Value],
  val text: String
) {
  val isA = EntityType.AccessPoint
}


case class AccessPoint(val e: Entity) extends Formable[AccessPointF] {
  import json.AccessPointFormat._
  lazy val formable: AccessPointF = Json.toJson(e).as[AccessPointF]
  lazy val formableOpt: Option[AccessPointF] = Json.toJson(e).asOpt[AccessPointF]
}

