package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}
import defines.EnumUtils._
import base._

import play.api.libs.json._
import models.json._
import play.api.libs.functional.syntax._

object HistoricalAgentF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
  final val IN_SET_REL = "inAuthoritativeSet"

  final val UNNAMED_PLACEHOLDER = "UNNAMED Authority"

  val PUBLICATION_STATUS = "publicationStatus"

  implicit object Converter extends RestConvertable[HistoricalAgentF] with ClientConvertable[HistoricalAgentF] {
    lazy val restFormat = models.json.HistoricalAgentFormat.restFormat

    private implicit val haDescFmt = HistoricalAgentDescriptionF.Converter.clientFormat
    lazy val clientFormat = Json.format[HistoricalAgentF]
  }
}

case class HistoricalAgentF(
  isA: EntityType.Value = EntityType.HistoricalAgent,
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(HistoricalAgentF.DESC_REL) descriptions: List[HistoricalAgentDescriptionF] = Nil
) extends Model with Persistable with Described[HistoricalAgentDescriptionF]


object HistoricalAgent {
  implicit object Converter extends ClientConvertable[HistoricalAgent] with RestReadable[HistoricalAgent] {
    val restReads = models.json.HistoricalAgentFormat.metaReads

    implicit val clientFormat: Format[HistoricalAgent] = (
      __.format[HistoricalAgentF](HistoricalAgentF.Converter.clientFormat) and
        (__ \ "set").formatNullable[AuthoritativeSet](AuthoritativeSet.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
      )(HistoricalAgent.apply _, unlift(HistoricalAgent.unapply _))

  }
}


case class HistoricalAgent(
  model: HistoricalAgentF,
  set: Option[AuthoritativeSet],
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent]
) extends AnyModel
  with MetaModel[HistoricalAgentF]
  with DescribedMeta[HistoricalAgentDescriptionF,HistoricalAgentF]
  with Accessible


