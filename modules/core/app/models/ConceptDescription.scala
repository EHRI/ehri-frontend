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
import eu.ehri.project.definitions.Ontology
import models.base.Description._


object ConceptDescriptionF {

  import eu.ehri.project.definitions.Ontology
  import backend.Entity._
  import models.ConceptF._

  implicit val conceptDescriptionFormat: Format[ConceptDescriptionF] = (
    (__ \ TYPE).formatIfEquals(EntityType.ConceptDescription) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ LANG_CODE).format[String] and
    (__ \ DATA \ IDENTIFIER).formatNullable[String] and
    (__ \ DATA \ PREFLABEL).format[String] and
    (__ \ DATA \ ALTLABEL).formatSeqOrSingleNullable[String] and
    (__ \ DATA \ HIDDENLABEL).formatSeqOrSingleNullable[String] and
    (__ \ DATA \ DEFINITION).formatSeqOrSingleNullable[String] and
    (__ \ DATA \ SCOPENOTE).formatSeqOrSingleNullable[String] and
    (__ \ DATA \ CREATION_PROCESS).formatWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ Ontology.HAS_ACCESS_POINT).formatSeqOrEmpty[AccessPointF] and
    (__ \ RELATIONSHIPS \ Ontology.HAS_MAINTENANCE_EVENT).formatSeqOrEmpty[MaintenanceEventF] and
    (__ \ RELATIONSHIPS \ Ontology.HAS_UNKNOWN_PROPERTY).formatSeqOrEmpty[Entity]
  )(ConceptDescriptionF.apply, unlift(ConceptDescriptionF.unapply))

  implicit object Converter extends Writable[ConceptDescriptionF] {
    lazy val restFormat: Format[ConceptDescriptionF] = conceptDescriptionFormat
  }
}

case class ConceptDescriptionF(
  isA: EntityType.Value = EntityType.ConceptDescription,
  id: Option[String],
  languageCode: String,
  identifier: Option[String] = None,
  name: String,
  altLabels: Option[Seq[String]] = None,
  hiddenLabels: Option[Seq[String]] = None,
  definition: Option[Seq[String]] = None,
  scopeNote: Option[Seq[String]] = None,
  creationProcess: Description.CreationProcess.Value = Description.CreationProcess.Manual,
  @models.relation(Ontology.HAS_ACCESS_POINT)
  accessPoints: Seq[AccessPointF] = Nil,
  @models.relation(Ontology.HAS_MAINTENANCE_EVENT)
  maintenanceEvents: Seq[MaintenanceEventF] = Nil,
  @models.relation(Ontology.HAS_UNKNOWN_PROPERTY)
  unknownProperties: Seq[Entity] = Nil
) extends Model with Persistable with Description {

  override def displayText: Option[String] =
    scopeNote.flatMap(_.headOption) orElse definition.flatMap(_.headOption)

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
    IDENTIFIER -> optional(nonEmptyText),
    PREFLABEL -> nonEmptyText,
    ALTLABEL -> optional(seq(nonEmptyText)),
    HIDDENLABEL -> optional(seq(nonEmptyText)),
    DEFINITION -> optional(seq(nonEmptyText)),
    SCOPENOTE -> optional(seq(nonEmptyText)),
    CREATION_PROCESS -> default(enumMapping(CreationProcess), CreationProcess.Manual),
    ACCESS_POINTS -> seq(AccessPoint.form.mapping),
    MAINTENANCE_EVENTS -> seq(MaintenanceEventF.form.mapping),
    UNKNOWN_DATA -> seq(entity)
  )(ConceptDescriptionF.apply)(ConceptDescriptionF.unapply))
}