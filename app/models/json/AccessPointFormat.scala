package models.json

import play.api.libs.json._
import models.{AccessPointF, Entity}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._


object AccessPointFormat extends JsonConverter[AccessPointF] {
  import Entity.{TYPE => ETYPE,_}
  import AccessPointF._

  implicit val accessPointTypeReads = enumReads(AccessPointType)

  implicit val accessPointReads: Reads[AccessPointF] = (
    (__ \ ETYPE).read[EntityType.Value](equalsReads(EntityType.AccessPoint)) andKeep
    (__ \ ID).readNullable[String] and
    ((__ \ DATA \ TYPE).read[AccessPointType.Value] orElse Reads.pure(AccessPointType.Other)) and
      // FIXME: Target should be consistent!!!
      ((__ \ DATA \ TARGET).read[String]
          orElse (__ \ DATA \ DESCRIPTION).read[String]
          orElse Reads.pure("Unknown target!")) and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(AccessPointF.apply _)

  implicit val accessPointWrites = new Writes[AccessPointF] {
    def writes(d: AccessPointF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          TYPE -> d.`type` ,
          TARGET -> d.name,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit val restFormat: Format[AccessPointF] = Format(accessPointReads,accessPointWrites)
}
