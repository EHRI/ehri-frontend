package models

import models.base.{MetaModel, Model, AnyModel}
import defines.EntityType
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models.json.JsPathExtensions
import com.fasterxml.jackson.core.JsonParseException
import services.{Entity, Readable}

/**
 * Class that holds data about a version of another item.
 */
case class VersionF(
  isA: EntityType.Value = EntityType.Version,
  id: Option[String] = None,
  itemType: EntityType.Value,
  itemId: String,
  itemData: Option[String]
) extends Model {
  def entity: Option[AnyModel] = try {
    for {
      data <- itemData
      item <- Json.parse(data).validate(AnyModel.Converter.restReads).asOpt
    } yield item
  } catch {
    case _: JsonParseException => None
  } 
}

object VersionF {
  import Entity._
  import eu.ehri.project.definitions.Ontology._

  implicit val reads: Reads[VersionF] = (
    (__ \ TYPE).readIfEquals(EntityType.Version) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ VERSION_ENTITY_CLASS).read[EntityType.Value] and
    (__ \ DATA \ VERSION_ENTITY_ID).read[String] and
    (__ \ DATA \ VERSION_ENTITY_DATA).readNullable[String]
  )(VersionF.apply _)

  implicit object Converter extends Readable[VersionF] {
    val restReads: Reads[VersionF] = reads
  }
}

case class Version(
  model: VersionF,
  event: Option[SystemEvent],
  meta: JsObject
) extends MetaModel[VersionF]

object Version {
  import Entity._
  import eu.ehri.project.definitions.Ontology._

  implicit val metaReads: Reads[Version] = (
    __.read[VersionF] and
    (__ \ RELATIONSHIPS \ VERSION_HAS_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Version.apply _)

  implicit object Converter extends Readable[Version] {
    val restReads: Reads[Version] = metaReads
  }
}