package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}
import base._

import play.api.libs.json._
import models.json._
import play.api.libs.functional.syntax._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import models.forms._
import play.api.libs.json.JsObject

object HistoricalAgentF {

  final val UNNAMED_PLACEHOLDER = "UNNAMED Authority"

  val PUBLICATION_STATUS = "publicationStatus"

  import Entity._
  import Ontology._

  implicit val historicalAgentWrites: Writes[HistoricalAgentF] = new Writes[HistoricalAgentF] {
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

  implicit val historicalAgentReads: Reads[HistoricalAgentF] = (
    (__ \ TYPE).readIfEquals(EntityType.HistoricalAgent) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).lazyReadNullable[List[HistoricalAgentDescriptionF]](
      Reads.list[HistoricalAgentDescriptionF]).map(_.getOrElse(List.empty[HistoricalAgentDescriptionF]))
  )(HistoricalAgentF.apply _)

  implicit val historicalAgentFormat: Format[HistoricalAgentF] = Format(historicalAgentReads,historicalAgentWrites)

  implicit object Converter extends RestConvertable[HistoricalAgentF] with ClientConvertable[HistoricalAgentF] {
    lazy val restFormat = historicalAgentFormat

    private implicit val haDescFmt = HistoricalAgentDescriptionF.Converter.clientFormat
    lazy val clientFormat = Json.format[HistoricalAgentF]
  }
}

case class HistoricalAgentF(
  isA: EntityType.Value = EntityType.HistoricalAgent,
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,

  @Annotations.Relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[HistoricalAgentDescriptionF] = Nil
) extends Model
  with Persistable
  with Described[HistoricalAgentDescriptionF]


object HistoricalAgent {
  import play.api.libs.functional.syntax._
  import Entity._
  import HistoricalAgentF._
  import Ontology._

  private implicit val systemEventReads = SystemEvent.Converter.restReads
  private implicit val authoritativeSetReads = AuthoritativeSet.Converter.restReads

  implicit val metaReads: Reads[HistoricalAgent] = (
    __.read[HistoricalAgentF] and
    (__ \ RELATIONSHIPS \ ITEM_IN_AUTHORITATIVE_SET).nullableHeadReads[AuthoritativeSet] and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).nullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyNullableHeadReads(
      SystemEvent.Converter.restReads) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(HistoricalAgent.apply _)

  implicit object Converter extends ClientConvertable[HistoricalAgent] with RestReadable[HistoricalAgent] {
    val restReads = metaReads

    implicit val clientFormat: Format[HistoricalAgent] = (
      __.format[HistoricalAgentF](HistoricalAgentF.Converter.clientFormat) and
      (__ \ "set").formatNullable[AuthoritativeSet](AuthoritativeSet.Converter.clientFormat) and
      (__ \ "accessibleTo").nullableListFormat(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(HistoricalAgent.apply _, unlift(HistoricalAgent.unapply _))
  }

  implicit object Resource extends RestResource[HistoricalAgent] {
    val entityType = EntityType.HistoricalAgent
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.HistoricalAgent),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures,
      PUBLICATION_STATUS -> optional(enum(defines.PublicationStatus)),
      "descriptions" -> list(HistoricalAgentDescription.form.mapping)
    )(HistoricalAgentF.apply)(HistoricalAgentF.unapply)
  )
}


case class HistoricalAgent(
  model: HistoricalAgentF,
  set: Option[AuthoritativeSet],
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[HistoricalAgentF]
  with DescribedMeta[HistoricalAgentDescriptionF,HistoricalAgentF]
  with Accessible


