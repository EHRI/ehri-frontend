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


object HistoricalAgentMeta {
  implicit object Converter extends ClientConvertable[HistoricalAgentMeta] with RestReadable[HistoricalAgentMeta] {
    val restReads = models.json.HistoricalAgentFormat.metaReads

    implicit val clientFormat: Format[HistoricalAgentMeta] = (
      __.format[HistoricalAgentF](HistoricalAgentF.Converter.clientFormat) and
        (__ \ "set").formatNullable[AuthoritativeSetMeta](AuthoritativeSetMeta.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
      )(HistoricalAgentMeta.apply _, unlift(HistoricalAgentMeta.unapply _))

  }
}


case class HistoricalAgentMeta(
  model: HistoricalAgentF,
  set: Option[AuthoritativeSetMeta],
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta]
) extends AnyModel
  with MetaModel[HistoricalAgentF]
  with DescribedMeta[HistoricalAgentDescriptionF,HistoricalAgentF]
  with Accessible


