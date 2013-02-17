package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.util._


import defines.PublicationStatus
import models.{Annotations, DatePeriodF, DocumentaryUnitDescriptionF}
import models.DocumentaryUnitDescriptionF._
import models.base.{TemporalEntity, DescribedEntity}
import org.joda.time.DateTime

case class TestDatePeriod(
  id: Option[String],
  startDate: Option[DateTime],
  endDate: Option[DateTime] = None
)

case class TestData(id: String, foo: String, bar: String)

case class TestDesc(
  id: Option[String],
  identifier: String,
  title: Option[String],
  languageCode: String,
  context: Context,
  content: Content,
  conditions: Conditions,
  materials: Materials,
  control: Control,
  @Annotations.Relation(TemporalEntity.DATE_REL)
  dates: List[TestDatePeriod]
)

case class TestDocumentaryUnit(
  id: Option[String],
  identifier: String,
  name: String,
  publicationStatus: Option[PublicationStatus.Value],
  descriptions: List[TestDesc]
)

object DocumentaryUnitFormat {
  import models.Entity._
  import models.IsadG._

  implicit val dateWrites = play.api.libs.json.Writes.jodaDateWrites("yyyy-MM-dd")

    implicit val testCaseWrites: Writes[TestData] = (
      (__ \ "id").write[String] and
        (__ \ "data" \ "foo").write[String] and
        (__ \ "data" \ "bar").write[String]
      )(unlift(TestData.unapply))

  implicit val testCaseFormat: Format[TestData] = (
    (__ \ "id").format[String] and
    (__ \ "data" \ "foo").format[String] and
    (__ \ "data" \ "bar").format[String]
  )(TestData.apply, unlift(TestData.unapply))

  implicit val datePeriodReads: Reads[TestDatePeriod] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ DatePeriodF.START_DATE).readNullable[DateTime] and
      (__ \ DATA \ DatePeriodF.END_DATE).readNullable[DateTime]
    )(TestDatePeriod)

  implicit val datePeriodWrites: Writes[TestDatePeriod] = (
    (__ \ ID).writeNullable[String] and
      (__ \ DATA \ DatePeriodF.START_DATE).write[Option[DateTime]] and
      (__ \ DATA \ DatePeriodF.END_DATE).write[Option[DateTime]]
    )(unlift(TestDatePeriod.unapply))

  implicit val datePeriodFormat: Format[TestDatePeriod] = Format(datePeriodReads,datePeriodWrites)

  /*implicit val datePeriodListFormat: Format[TestDatePeriod] = (
      (__ \ ID).formatNullable[String] and
      (__ \ DATA \ DatePeriodF.START_DATE).formatNullable[DateTime] and
      (__ \ DATA \ DatePeriodF.END_DATE).formatNullable[DateTime]
    )(TestDatePeriod.apply, unlift(TestDatePeriod.unapply))*/

  implicit val pubStatusFormat = defines.EnumFormat.enumFormat(PublicationStatus)
  

  // NON-Working Format -items in the data section get overwritten
  // by subsequent items.

  implicit val descFormat: Format[TestDesc] = (
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ TITLE).formatNullable[String] and
    (__ \ DATA \ LANG_CODE).format[String] and
    (__ \ DATA).format[Context]((
      (__ \ ADMIN_BIOG).formatNullable[String] and
      (__ \ ARCH_HIST).formatNullable[String] and
      (__ \ ACQUISITION).formatNullable[String]
      )(Context.apply, unlift(Context.unapply))) and
    (__ \ DATA).format[Content]((
        (__ \ SCOPE_CONTENT).formatNullable[String] and
        (__ \ APPRAISAL).formatNullable[String] and
        (__ \ ACCRUALS).formatNullable[String] and
        (__ \ SYS_ARR).formatNullable[String]
      )(Content.apply, unlift(Content.unapply))) and
    (__ \ DATA).format[Conditions]((
        (__ \ ACCESS_COND).formatNullable[String] and
        (__ \ REPROD_COND).formatNullable[String] and
        (__ \ PHYSICAL_CHARS).formatNullable[String] and
        (__ \ FINDING_AIDS).formatNullable[String]
      )(Conditions.apply, unlift(Conditions.unapply))) and
    (__ \ DATA).format[Materials]((
        (__ \ LOCATION_ORIGINALS).formatNullable[String] and
        (__ \ LOCATION_COPIES).formatNullable[String] and
        (__ \ RELATED_UNITS).formatNullable[String] and
        (__ \ PUBLICATION_NOTE).formatNullable[String]
      )(Materials.apply, unlift(Materials.unapply))) and
    (__ \ DATA).format[Control]((
        (__ \ ARCHIVIST_NOTE).formatNullable[String] and
        (__ \ RULES_CONVENTIONS).formatNullable[String] and
        (__ \ DATES_DESCRIPTIONS).formatNullable[String]
      )(Control.apply, unlift(Control.unapply))) and
      (__ \ RELATIONSHIPS \ TemporalEntity.DATE_REL).lazyFormat[List[TestDatePeriod]](
      Reads.list[TestDatePeriod], Writes.traversableWrites[TestDatePeriod])
    )(TestDesc.apply, unlift(TestDesc.unapply))




  implicit val documentaryUnitFormat: Format[TestDocumentaryUnit] = (
    (__ \ ID).formatNullable[String] and
      (__ \ DATA \ IDENTIFIER).format[String] and
      (__ \ DATA \ NAME).format[String] and
      (__ \ DATA \ PUB_STATUS).formatNullable[PublicationStatus.Value] and
      (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyFormat[List[TestDesc]](
        Reads.list[TestDesc], Writes.traversableWrites[TestDesc])
    )(TestDocumentaryUnit.apply, unlift(TestDocumentaryUnit.unapply))
}