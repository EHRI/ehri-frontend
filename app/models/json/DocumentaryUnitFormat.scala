package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.util._
import defines.EnumUtils._


import defines.{EntityType, PublicationStatus}
import models.base.{AccessibleEntity, DescribedEntity}
import models.{SystemEventMeta, RepositoryMeta, SystemEvent, DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnitMeta}


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
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.DocumentaryUnit)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      ((__ \ DATA \ COPYRIGHT).read[Option[CopyrightStatus.Value]] orElse Reads.pure(Some(CopyrightStatus.Unknown))) and
      (__ \ DATA \ SCOPE).readNullable[Scope.Value] and
      (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[DocumentaryUnitDescriptionF]](
        Reads.list[DocumentaryUnitDescriptionF])
    )(DocumentaryUnitF.apply _)

  implicit val restFormat: Format[DocumentaryUnitF] = Format(documentaryUnitReads,documentaryUnitWrites)

  private lazy implicit val repoReads = RepositoryFormat.metaReads
  private lazy implicit val systemEventReads = SystemEventFormat.metaReads

  implicit val metaReads: Reads[DocumentaryUnitMeta] = (
    __.read[JsObject] and // capture the full JS data
    __.read[DocumentaryUnitF] and
    // Holder
    //(__ \ RELATIONSHIPS \ DocumentaryUnitF.HELD_REL).lazyReadNullable[List[RepositoryMeta]](
    //  Reads.list[RepositoryMeta]).map(_.flatMap(_.headOption)) and
    //
    (__ \ RELATIONSHIPS \ DocumentaryUnitF.CHILD_REL).lazyReadNullable[List[DocumentaryUnitMeta]](
      Reads.list[DocumentaryUnitMeta]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list[SystemEventMeta]).map(_.flatMap(_.headOption))
  )(DocumentaryUnitMeta.apply _)
}