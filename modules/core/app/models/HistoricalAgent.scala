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
import eu.ehri.project.definitions.Ontology
import solr.{SolrIndexer, SolrConstants}
import solr.SolrConstants._
import play.api.libs.json.JsString
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
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
      )(HistoricalAgent.apply _, unlift(HistoricalAgent.unapply _))

  }

  val toSolr: JsObject => Seq[JsObject] = { js =>
    import SolrConstants._
    val c = js.as[HistoricalAgent](Converter.restReads)
    val descriptionData = (js \ Entity.RELATIONSHIPS \ Ontology.DESCRIPTION_FOR_ENTITY)
      .asOpt[List[JsObject]].getOrElse(List.empty[JsObject])

    c.descriptions.zipWithIndex.map { case (desc, i) =>
      val data = SolrIndexer.dynamicData((descriptionData(i) \ Entity.DATA).as[JsObject])
      data ++ Json.obj(
        ID -> Json.toJson(desc.id),
        TYPE -> JsString(c.isA.toString),
        NAME_EXACT -> JsString(desc.name),
        LANGUAGE_CODE -> JsString(desc.languageCode),
        ITEM_ID -> JsString(c.id),
        RESTRICTED_FIELD -> JsBoolean(if (c.accessors.isEmpty) false else true),
        ACCESSOR_FIELD -> c.accessors.map(_.id),
        LAST_MODIFIED -> c.latestEvent.map(_.model.datetime),
        OTHER_NAMES -> Json.toJson(desc.otherFormsOfName.toList.flatten),
        PARALLEL_NAMES -> Json.toJson(desc.parallelFormsOfName.toList.flatten),
        HOLDER_ID -> Json.toJson(c.set.map(_.id)),
        HOLDER_NAME -> Json.toJson(c.set.map(_.toStringLang)),
        Isaar.IDENTIFIER -> c.model.identifier,
        Isaar.ENTITY_TYPE -> Json.toJson(desc.entityType.toString)
      )
    }
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


