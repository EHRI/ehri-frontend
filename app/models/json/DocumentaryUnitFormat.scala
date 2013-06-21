package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.util._
import defines.EnumUtils._


import defines.{EntityType, PublicationStatus}
import models.base.DescribedEntity
import models.{DocumentaryUnitDescriptionF, DocumentaryUnitF}


object DocumentaryUnitFormat {
  import models.json.IsadGFormat._
  import models.Entity._
  import models.DocumentaryUnitF._

  implicit val publicationStatusReads = defines.EnumUtils.enumReads(PublicationStatus)
  implicit val copyrightStatusReads = defines.EnumUtils.enumReads(CopyrightStatus)
  implicit val scopeReads = defines.EnumUtils.enumReads(Scope)

  implicit val documentaryUnitWrites: Writes[DocumentaryUnitF] = new Writes[DocumentaryUnitF] {
    def writes(d: DocumentaryUnitF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          PUBLICATION_STATUS -> d.publicationStatus,
          COPYRIGHT -> d.copyrightStatus.orElse(Some(CopyrightStatus.Unknown)),
          SCOPE -> d.scope
        ),
        RELATIONSHIPS -> Json.obj(
          DESC_REL -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val documentaryUnitReads: Reads[DocumentaryUnitF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.DocumentaryUnit)) andKeep
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      ((__ \ DATA \ COPYRIGHT).read[Option[CopyrightStatus.Value]] orElse Reads.pure(Some(CopyrightStatus.Unknown))) and
      (__ \ DATA \ SCOPE).readNullable[Scope.Value] and
      (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[DocumentaryUnitDescriptionF]](
        Reads.list[DocumentaryUnitDescriptionF])
    )(DocumentaryUnitF.apply _)

  implicit val documentaryUnitFormat: Format[DocumentaryUnitF] = Format(documentaryUnitReads,documentaryUnitWrites)
}