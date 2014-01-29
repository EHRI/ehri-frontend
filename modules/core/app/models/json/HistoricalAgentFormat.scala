package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._


import defines.{EntityType, PublicationStatus}
import models.base.{Described, Accessible, Accessor}
import models._
import play.api.data.validation.ValidationError
import defines.EnumUtils._
import eu.ehri.project.definitions.Ontology


object HistoricalAgentFormat {
  import models.json.IsaarFormat._
  import models.Entity._
  import models.HistoricalAgentF._
  import Ontology._

  implicit val publicationStatusReads = defines.EnumUtils.enumReads(PublicationStatus)

  implicit val actorWrites: Writes[HistoricalAgentF] = new Writes[HistoricalAgentF] {
    def writes(d: HistoricalAgentF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          PUBLICATION_STATUS -> d.publicationStatus
        ),
        RELATIONSHIPS -> Json.obj(
          DESCRIPTION_FOR_ENTITY -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val actorReads: Reads[HistoricalAgentF] = (
      (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.HistoricalAgent)) and
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).lazyReadNullable[List[HistoricalAgentDescriptionF]](
        Reads.list[HistoricalAgentDescriptionF]).map(_.getOrElse(List.empty[HistoricalAgentDescriptionF]))
    )(HistoricalAgentF.apply _)

  implicit val restFormat: Format[HistoricalAgentF] = Format(actorReads,actorWrites)


  private implicit val systemEventReads = SystemEventFormat.metaReads
  private implicit val authoritativeSetReads = AuthoritativeSetFormat.metaReads

  implicit val metaReads: Reads[HistoricalAgent] = (
    __.read[HistoricalAgentF] and
    (__ \ RELATIONSHIPS \ ITEM_IN_AUTHORITATIVE_SET).lazyReadNullable[List[AuthoritativeSet]](
      Reads.list[AuthoritativeSet]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption)) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(HistoricalAgent.apply _)
}