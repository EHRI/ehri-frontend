package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.util._


import defines.PublicationStatus
import models.DocumentaryUnitDescriptionF
import models.DocumentaryUnitDescriptionF._
import models.base.DescribedEntity

case class TestDesc(
  id: Option[String],
  identifier: String,
  context: Context,
  content: Content,
  conditions: Conditions,
  materials: Materials,
  control: Control
)

case class TestDocumentaryUnit(
  id: Option[String],
  identifier: String,
  name: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  descriptions: List[TestDesc] = Nil
)

object DocumentaryUnitFormat {
  import models.Entity._
  import models.IsadG._

  implicit val pubStatusFormat = defines.EnumFormat.enumFormat(PublicationStatus)
  
  implicit val descFormat: Format[TestDesc] = (
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
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
      )(Control.apply, unlift(Control.unapply)))
    )(TestDesc.apply, unlift(TestDesc.unapply))

  implicit val documentaryUnitReads: Reads[TestDocumentaryUnit] = (
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ NAME).read[String] and
    (__ \ DATA \ PUB_STATUS).readNullable[PublicationStatus.Value] and
    (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead(Reads.list[TestDesc])
  )(TestDocumentaryUnit)

  implicit val documentaryUnitWrites: Writes[TestDocumentaryUnit] = (
    (__ \ ID).writeNullable[String] and
      (__ \ DATA \ IDENTIFIER).write[String] and
      (__ \ DATA \ NAME).write[String] and
      (__ \ DATA \ PUB_STATUS).writeNullable[PublicationStatus.Value] and
      (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyWrite(Writes.traversableWrites[TestDesc])
    )(unlift(TestDocumentaryUnit.unapply))

  implicit val documentaryUnitFormat: Format[TestDocumentaryUnit] = Format(documentaryUnitReads,documentaryUnitWrites)
  
}