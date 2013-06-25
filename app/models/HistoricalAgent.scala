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
) extends Persistable

case class HistoricalAgent(val e: Entity)
  extends AccessibleEntity
  with AnnotatableEntity
  with LinkableEntity
  with DescribedEntity[HistoricalAgentDescription]
  with Formable[HistoricalAgentF] {
  def descriptions: List[HistoricalAgentDescription] = e.relations(DescribedEntity.DESCRIBES_REL)
      .map(HistoricalAgentDescription(_)).sortBy(d => d.languageCode)

  val set: Option[AuthoritativeSet] = e.relations(HistoricalAgentF.IN_SET_REL).headOption.map(AuthoritativeSet(_))

  val publicationStatus = e.property(HistoricalAgentF.PUBLICATION_STATUS).flatMap(enumReads(PublicationStatus).reads(_).asOpt)

  lazy val formable: HistoricalAgentF = Json.toJson(e).as[HistoricalAgentF]
  lazy val formableOpt: Option[HistoricalAgentF] = Json.toJson(e).asOpt[HistoricalAgentF]

  override def toString = {
    descriptions.headOption.flatMap(d => d.stringProperty(Isdiah.AUTHORIZED_FORM_OF_NAME)).getOrElse(id)
  }
}


object HistoricalAgentMeta {
  implicit object Converter extends ClientConvertable[HistoricalAgentMeta] with RestReadable[HistoricalAgentMeta] {
    val restReads = models.json.HistoricalAgentFormat.metaReads
    val clientFormat = models.json.client.historicalAgentMetaFormat
  }
}


case class HistoricalAgentMeta(
  model: HistoricalAgentF,
  set: Option[AuthoritativeSetMeta],
  latestEvent: Option[SystemEventMeta]
) extends MetaModel[HistoricalAgentF]


