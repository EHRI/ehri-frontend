package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus}
import defines.EnumUtils._
import base._

import play.api.libs.json._

object HistoricalAgentF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
  final val IN_SET_REL = "inAuthoritativeSet"

  final val UNNAMED_PLACEHOLDER = "UNNAMED Authority"

  val PUBLICATION_STATUS = "publicationStatus"

  lazy implicit val jsonFormat = json.HistoricalAgentFormat.historicalAgentFormat
}

case class HistoricalAgentF(
  id: Option[String],
  identifier: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(HistoricalAgentF.DESC_REL) descriptions: List[HistoricalAgentDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.HistoricalAgent

  def toJson: JsValue = Json.toJson(this)
}

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




