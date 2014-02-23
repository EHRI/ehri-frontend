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

  @Annotations.Relation(Ontology.DESCRIPTION_FOR_ENTITY)
  descriptions: List[HistoricalAgentDescriptionF] = Nil
) extends Model
  with Persistable
  with Described[HistoricalAgentDescriptionF]


object HistoricalAgent {
  implicit object Converter extends ClientConvertable[HistoricalAgent] with RestReadable[HistoricalAgent] {
    val restReads = models.json.HistoricalAgentFormat.metaReads

    implicit val clientFormat: Format[HistoricalAgent] = (
      __.format[HistoricalAgentF](HistoricalAgentF.Converter.clientFormat) and
        (__ \ "set").formatNullable[AuthoritativeSet](AuthoritativeSet.Converter.clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(HistoricalAgent.apply _, unlift(HistoricalAgent.unapply _))

  }

  implicit object Resource extends RestResource[HistoricalAgent] {
    val entityType = EntityType.HistoricalAgent
  }

  import HistoricalAgentF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.HistoricalAgent),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures,
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


