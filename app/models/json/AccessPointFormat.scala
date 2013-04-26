package models.json

import play.api.libs.json._
import models.{AccessPointF, Entity}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._


object AccessPointFormat {
  import Entity.{TYPE => ETYPE,_}
  import AccessPointF._

  implicit val accessPointReads: Reads[AccessPointF] = (
    (__ \ ETYPE).read[EntityType.Value](equalsReads(EntityType.AccessPoint)) andKeep
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ TYPE).readNullable[EntityType.Value] and
      (__ \ DATA \ DESCRIPTION).read[String]
    )(AccessPointF.apply _)

  implicit val accessPointWrites = new Writes[AccessPointF] {
    def writes(d: AccessPointF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          TYPE -> d.`type` ,
          DESCRIPTION -> d.text
        )
      )
    }
  }
}