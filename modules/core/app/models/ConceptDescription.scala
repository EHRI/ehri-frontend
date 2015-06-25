package models

import defines.EntityType
import models.base._
import models.json._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.data.Form
import play.api.data.Forms._
import utils.forms._
import backend.{Entity, Writable}

import Description._
import models.base.Description.CREATION_PROCESS

object ConceptDescriptionF {

  import eu.ehri.project.definitions.Ontology
  import Entity._
  import ConceptF._

  implicit val conceptDescriptionWrites = new Writes[ConceptDescriptionF] {
    def writes(d: ConceptDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          LANG_CODE -> d.languageCode,
          PREFLABEL -> d.name,
          ALTLABEL -> d.altLabels,
          DEFINITION -> d.definition,
          SCOPENOTE -> d.scopeNote,
          LONGITUDE -> d.longitude,
          LATITUDE -> d.latitude,
          URL -> d.url,
          CREATION_PROCESS -> d.creationProcess
        ),
        RELATIONSHIPS -> Json.obj(
          Ontology.HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          Ontology.HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val conceptDescriptionReads: Reads[ConceptDescriptionF] = (
    (__ \ TYPE).readIfEquals(EntityType.ConceptDescription) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ LANG_CODE).read[String] and
    (__ \ DATA \ PREFLABEL).read[String] and
    (__ \ DATA \ ALTLABEL).readSeqOrSingleNullable[String] and
    (__ \ DATA \ DEFINITION).readSeqOrSingleNullable[String] and
    (__ \ DATA \ SCOPENOTE).readSeqOrSingleNullable[String] and
    (__ \ DATA \ LONGITUDE).readNullable[BigDecimal] and
    (__ \ DATA \ LATITUDE).readNullable[BigDecimal] and
    (__ \ DATA \ URL).readNullable[String] and
    (__ \ DATA \ CREATION_PROCESS).readWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ Ontology.HAS_ACCESS_POINT).nullableSeqReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ Ontology.HAS_UNKNOWN_PROPERTY).nullableSeqReads[Entity]
  )(ConceptDescriptionF.apply _)

  implicit val conceptDescriptionFormat: Format[ConceptDescriptionF] = Format(conceptDescriptionReads,conceptDescriptionWrites)

  implicit object Converter extends Writable[ConceptDescriptionF] {
    lazy val restFormat = conceptDescriptionFormat
  }
}

case class ConceptDescriptionF(
  isA: EntityType.Value = EntityType.ConceptDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  altLabels: Option[Seq[String]] = None,
  definition: Option[Seq[String]] = None,
  scopeNote: Option[Seq[String]] = None,
  longitude: Option[BigDecimal] = None,
  latitude: Option[BigDecimal] = None,
  url: Option[String] = None,
  creationProcess: Description.CreationProcess.Value = Description.CreationProcess.Manual,
  accessPoints: Seq[AccessPointF] = Nil,
  unknownProperties: Seq[Entity] = Nil
) extends Model with Persistable with Description {

  def displayText = scopeNote.flatMap(_.headOption) orElse definition.flatMap(_.headOption)

  // NA - no single valued optional text fields
  // here...
  def toSeq = Seq()
}

object ConceptDescription {

  import ConceptF._
  import Entity._
  import defines.EnumUtils.enumMapping

  val form = Form(mapping(
    ISA -> ignored(EntityType.ConceptDescription),
    ID -> optional(nonEmptyText),
    LANG_CODE -> nonEmptyText,
    PREFLABEL -> nonEmptyText,
    ALTLABEL -> optional(seq(nonEmptyText)),
    DEFINITION -> optional(seq(nonEmptyText)),
    SCOPENOTE -> optional(seq(nonEmptyText)),
    LONGITUDE -> optional(bigDecimal),
    LATITUDE -> optional(bigDecimal),
    URL -> optional(nonEmptyText),
    CREATION_PROCESS -> default(enumMapping(CreationProcess), CreationProcess.Manual),
    ACCESS_POINTS -> seq(AccessPoint.form.mapping),
    UNKNOWN_DATA -> seq(entity)
  )(ConceptDescriptionF.apply)(ConceptDescriptionF.unapply))
}