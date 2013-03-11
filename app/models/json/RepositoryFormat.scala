package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._


import defines.PublicationStatus
import models.base.DescribedEntity
import models._


object RepositoryFormat {
  import defines.EnumWriter.enumWrites
  import models.json.IsdiahFormat._
  import models.Entity._
  import models.RepositoryF._

  implicit val publicationStatusReads = defines.EnumReader.enumReads(PublicationStatus)

  implicit val repositoryWrites: Writes[RepositoryF] = new Writes[RepositoryF] {
    def writes(d: RepositoryF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          PUBLICATION_STATUS -> d.publicationStatus,
          PRIORITY -> d.priority
        ),
        RELATIONSHIPS -> Json.obj(
          DESC_REL -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val repositoryReads: Reads[RepositoryF] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).read[String] and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[RepositoryDescriptionF]](
        Reads.list[RepositoryDescriptionF]) and
      (__ \ DATA \ PRIORITY).readNullable[Int]
    )(RepositoryF.apply _)

  implicit val repositoryFormat: Format[RepositoryF] = Format(repositoryReads,repositoryWrites)
}