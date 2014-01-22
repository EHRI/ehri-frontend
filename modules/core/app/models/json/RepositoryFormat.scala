package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._


import defines.{EntityType, PublicationStatus}
import models.base.{Described, Accessor}
import models._
import eu.ehri.project.definitions.Ontology
import defines.EnumUtils._


object RepositoryFormat {
  import models.json.IsdiahFormat._
  import models.Entity._
  import models.RepositoryF._
  import Ontology._

  implicit val publicationStatusReads = defines.EnumUtils.enumReads(PublicationStatus)

  implicit val repositoryWrites: Writes[RepositoryF] = new Writes[RepositoryF] {
    def writes(d: RepositoryF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          PUBLICATION_STATUS -> d.publicationStatus,
          PRIORITY -> d.priority,
          URL_PATTERN -> d.urlPattern
        ),
        RELATIONSHIPS -> Json.obj(
          DESCRIPTION_FOR_ENTITY -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val repositoryReads: Reads[RepositoryF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Repository)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      // FIXME: This throws an error if an item has no descriptions - we should somehow
      // make it so that the path being missing is permissable but a validation error
      // is not.
      (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).lazyReadNullable[List[RepositoryDescriptionF]](
        Reads.list[RepositoryDescriptionF]).map(_.getOrElse(List.empty[RepositoryDescriptionF])) and
      (__ \ DATA \ PRIORITY).readNullable[Int] and
      (__ \ DATA \ URL_PATTERN).readNullable[String]
    )(RepositoryF.apply _)

  implicit val restFormat: Format[RepositoryF] = Format(repositoryReads,repositoryWrites)

  implicit lazy val metaReads: Reads[Repository] = (
    __.read[RepositoryF](repositoryReads) and
    // Country
    (__ \ RELATIONSHIPS \ REPOSITORY_HAS_COUNTRY).lazyReadNullable[List[Country]](
      Reads.list(CountryFormat.metaReads)).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
        Reads.list(Accessor.Converter.restReads)).map(_.toList.flatten) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
        Reads.list(SystemEventFormat.metaReads)).map(_.flatMap(_.headOption)) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(Repository.apply _)

}