package models

import defines._
import models.base._


import forms._
import play.api.data._
import play.api.data.Forms._
import models.base.{DescribedEntity, AttributeSet, Persistable, TemporalEntity}
import play.api.libs.json.{Json, JsString, JsValue}
import defines.EnumWriter.enumWrites
import defines.enum

object CopyrightStatus extends Enumeration {
  val Yes = Value("yes")
  val No = Value("no")
  val Unknown = Value("unknown")
}

object Scope extends Enumeration {
  val High = Value("high")
  val Medium = Value("medium")
  val Low = Value("low")
}


object DocumentaryUnitF {

  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"
  final val SCOPE = "scope"
  final val COPYRIGHT = "copyright"

  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "heldBy"
  final val CHILD_REL = "childOf"

}

case class DocumentaryUnitF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  val copyrightStatus: Option[CopyrightStatus.Value] = Some(CopyrightStatus.Unknown),
  val scope: Option[Scope.Value] = Some(Scope.Low),

  @Annotations.Relation(DocumentaryUnitF.DESC_REL)
  val descriptions: List[DocumentaryUnitDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.DocumentaryUnit

  def withDescription(d: DocumentaryUnitDescriptionF): DocumentaryUnitF = copy(descriptions = descriptions ++ List(d))

  /**
   * Get a description with a given id.
   * @param did
   * @return
   */
  def description(did: String): Option[DocumentaryUnitDescriptionF] = descriptions.find(d => d.id.isDefined && d.id.get == did)

  /**
   * Replace an existing description with the same id as this one, or add
   * this one to the end of the list of descriptions.
   * @param d
   * @return
   */
  def replaceDescription(d: DocumentaryUnitDescriptionF): DocumentaryUnitF = d.id.map {
    did =>
    // If the description has an id, replace the existing one with that id
      val newDescriptions = descriptions.map {
        dm =>
          if (dm.id.isDefined && dm.id.get == did) d else dm
      }
      copy(descriptions = newDescriptions)
  } getOrElse {
    withDescription(d)
  }

  import json.DocumentaryUnitFormat._
  def toJson: JsValue = Json.toJson(this)
}

case class DocumentaryUnitDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val title: Option[String] = None,
  @Annotations.Relation(TemporalEntity.DATE_REL)
  val dates: List[DatePeriodF] = Nil,
  val extentAndMedium: Option[String] = None,
  val context: DocumentaryUnitDescriptionF.Context,
  val content: DocumentaryUnitDescriptionF.Content,
  val conditions: DocumentaryUnitDescriptionF.Conditions,
  val materials: DocumentaryUnitDescriptionF.Materials,
  val control: DocumentaryUnitDescriptionF.Control
) extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription

  import json.IsadGFormat._
  def toJson: JsValue = Json.toJson(this)
}

object DocumentaryUnitDescriptionF {

  case class Context(
    val adminBiogHistory: Option[String] = None,
    val archivalHistory: Option[String] = None,
    val acquisition: Option[String] = None
  ) extends AttributeSet

  case class Content(
    val scopeAndContent: Option[String] = None,
    val appraisal: Option[String] = None,
    val accruals: Option[String] = None,
    val systemOfArrangement: Option[String] = None
  ) extends AttributeSet

  case class Conditions(
    val conditionsOfAccess: Option[String] = None,
    val conditionsOfReproduction: Option[String] = None,
    val languageOfMaterials: Option[List[String]] = None,
    val scriptOfMaterials: Option[List[String]] = None,
    val physicalCharacteristics: Option[String] = None,
    val findingAids: Option[String] = None
  ) extends AttributeSet

  case class Materials(
    val locationOfOriginals: Option[String] = None,
    val locationOfCopies: Option[String] = None,
    val relatedUnitsOfDescription: Option[String] = None,
    val publicationNote: Option[String] = None
  ) extends AttributeSet

  case class Control(
    val archivistNote: Option[String] = None,
    val rulesAndConventions: Option[String] = None,
    val datesOfDescriptions: Option[String] = None
  )
}

object DocumentaryUnitDescriptionForm {

  import DocumentaryUnitDescriptionF._
  import IsadG._
  import Entity._

  val form = Form(
    mapping(
      ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      TITLE -> optional(nonEmptyText),
      DATES -> list(DatePeriodForm.form.mapping),
      EXTENT_MEDIUM -> optional(nonEmptyText),
      CONTEXT_AREA -> mapping(
        ADMIN_BIOG -> optional(text),
        ARCH_HIST -> optional(text),
        ACQUISITION -> optional(text)
      )(Context.apply)(Context.unapply),
      CONTENT_AREA -> mapping(
        SCOPE_CONTENT -> optional(text),
        APPRAISAL -> optional(text),
        ACCRUALS -> optional(text),
        SYS_ARR -> optional(text)
      )(Content.apply)(Content.unapply),
      CONDITIONS_AREA -> mapping(
        ACCESS_COND -> optional(text),
        REPROD_COND -> optional(text),
        LANG_MATERIALS -> optional(list(nonEmptyText)),
        SCRIPT_MATERIALS -> optional(list(nonEmptyText)),
        PHYSICAL_CHARS -> optional(text),
        FINDING_AIDS -> optional(text)
      )(Conditions.apply)(Conditions.unapply),
      MATERIALS_AREA -> mapping(
        LOCATION_ORIGINALS -> optional(text),
        LOCATION_COPIES -> optional(text),
        RELATED_UNITS -> optional(text),
        PUBLICATION_NOTE -> optional(text)
      )(Materials.apply)(Materials.unapply),
      CONTROL_AREA -> mapping(
        ARCHIVIST_NOTE -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        DATES_DESCRIPTIONS -> optional(text)
      )(Control.apply)(Control.unapply)
    )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
  )
}

object DocumentaryUnitForm {

  import Entity._
  import IsadG._
  import DocumentaryUnitF._

  val form = Form(
    mapping(
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      PUB_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      COPYRIGHT -> optional(models.forms.enum(CopyrightStatus)),
      SCOPE -> optional(models.forms.enum(Scope)),
      DescribedEntity.DESCRIPTIONS -> list(DocumentaryUnitDescriptionForm.form.mapping)
    )(DocumentaryUnitF.apply)(DocumentaryUnitF.unapply)
  )
}


case class DocumentaryUnit(val e: Entity) extends NamedEntity
with AccessibleEntity
with AnnotatableEntity
with HierarchicalEntity[DocumentaryUnit]
with DescribedEntity
with Formable[DocumentaryUnitF] {

  import DocumentaryUnitF._
  import DescribedEntity._

  val hierarchyRelationName = CHILD_REL

  val holder: Option[Repository] = e.relations(HELD_REL).headOption.map(Repository(_))
  val parent: Option[DocumentaryUnit] = e.relations(CHILD_REL).headOption.map(DocumentaryUnit(_))
  val publicationStatus = e.property(IsadG.PUB_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  // NB: There is a default value of copyright status, so use 'unknown'.
  val copyrightStatus = e.property(COPYRIGHT).flatMap(enum(CopyrightStatus).reads(_).asOpt)
        .orElse(Some(CopyrightStatus.Unknown))
  val scope = e.property(SCOPE).flatMap(enum(Scope).reads(_).asOpt)

  override def descriptions: List[DocumentaryUnitDescription] = e.relations(DESCRIBES_REL).map(DocumentaryUnitDescription(_))

  import json.DocumentaryUnitFormat._
  def formable: DocumentaryUnitF = Json.toJson(e).as[DocumentaryUnitF]
}

case class DocumentaryUnitDescription(val e: Entity)
  extends Description
  with TemporalEntity
  with Formable[DocumentaryUnitDescriptionF] {

  // This should have one logical object
  val item: Option[DocumentaryUnit] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(DocumentaryUnit(_))

  import models.IsadG._
  import DocumentaryUnitDescriptionF._

  import json.IsadGFormat._
  def formable = Json.toJson(e).as[DocumentaryUnitDescriptionF]
}
