package models.json

import play.api.libs.json._
import models.{DatePeriodType, Entity, DatePeriodF}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._


object DatePeriodFormat {
  import Entity.{TYPE => ETYPE,_}
  import DatePeriodF._
  import defines.EnumReader.enumReads

  implicit val datePeriodTypeReads = enumReads(DatePeriodType)

  implicit val datePeriodReads: Reads[DatePeriodF] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ TYPE).readNullable[DatePeriodType.Value] and
      (__ \ DATA \ START_DATE).read[DateTime] and
      (__ \ DATA \ END_DATE).read[DateTime]
    )(DatePeriodF.apply _)

  import defines.EnumWriter.enumWrites
  implicit val datePeriodWrites: Writes[DatePeriodF] = (
    (__ \ ID).writeNullable[String] and
      (__ \ DATA \ TYPE).writeNullable[DatePeriodType.Value] and
      (__ \ DATA \ START_DATE).write[DateTime] and
      (__ \ DATA \ END_DATE).write[DateTime]
    )(unlift(DatePeriodF.unapply))
}