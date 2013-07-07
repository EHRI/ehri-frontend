package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}
import defines.EnumUtils._
import base._

import play.api.libs.json._
import models.json.{RestReadable, ClientConvertable, RestConvertable}

object HistoricalAgentF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
  final val IN_SET_REL = "inAuthoritativeSet"

  final val UNNAMED_PLACEHOLDER = "UNNAMED Authority"

  val PUBLICATION_STATUS = "publicationStatus"

  lazy implicit val jsonFormat = json.HistoricalAgentFormat.restFormat


  implicit object Converter extends RestConvertable[HistoricalAgentF] with ClientConvertable[HistoricalAgentF] {
    lazy val restFormat = models.json.rest.historicalAgentFormat
    lazy val clientFormat = models.json.client.historicalAgentFormat
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
    val clientFormat = models.json.client.historicalAgentMetaFormat

    AnyModel.registerRest(EntityType.HistoricalAgent, restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerClient(EntityType.HistoricalAgent, clientFormat.asInstanceOf[Format[AnyModel]])
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


