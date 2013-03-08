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

case object IsadG {
  /* ISAD(G)-based field set */
  val NAME = "name"
  val TITLE = "title"
  val DATES = "dates"
  val EXTENT_MEDIUM = "extentAndMedium"
  val PUB_STATUS = "publicationStatus"
  val LANG_CODE = "languageCode"

  val IDENTITY_AREA = "identityArea"
  val DESCRIPTIONS_AREA = "descriptionsArea"
  val ADMINISTRATION_AREA = "administrationArea"

  val CONTEXT_AREA = "contextArea"
  val ADMIN_BIOG = "adminBiogHistory"
  val ARCH_HIST = "archivalHistory"
  val ACQUISITION = "acquisition"

  val CONTENT_AREA = "contentArea"
  val SCOPE_CONTENT = "scopeAndContent"
  val APPRAISAL = "appraisal"
  val ACCRUALS = "accruals"
  val SYS_ARR = "systemOfArrangement"

  val CONDITIONS_AREA = "conditionsArea"
  val ACCESS_COND = "conditionsOfAccess"
  val REPROD_COND = "conditionsOfReproduction"
  val LANG_MATERIALS = "languageOfMaterials"
  val SCRIPT_MATERIALS = "scriptOfMaterials"
  val PHYSICAL_CHARS = "physicalCharacteristics"
  val FINDING_AIDS = "findingAids"

  val MATERIALS_AREA = "materialsArea"
  val LOCATION_ORIGINALS = "locationOfOriginals"
  val LOCATION_COPIES = "locationOfCopies"
  val RELATED_UNITS = "relatedUnitsOfDescription"
  val PUBLICATION_NOTE = "publicationNote"

  val CONTROL_AREA = "controlArea"
  val ARCHIVIST_NOTE = "archivistNote"
  val RULES_CONVENTIONS = "rulesAndConventions"
  val DATES_DESCRIPTIONS = "datesOfDescriptions"
}


object DocumentaryUnitF {

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

  def toJson: JsValue = {
    import Entity._
    import IsadG._
    import DocumentaryUnitF._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        IDENTIFIER -> identifier,
        NAME -> name,
        PUB_STATUS -> publicationStatus,
        COPYRIGHT -> copyrightStatus.orElse(Some(CopyrightStatus.Unknown)),
        SCOPE -> scope
      ),
      RELATIONSHIPS -> Json.obj(
        DESC_REL -> Json.toJson(descriptions.map(_.toJson).toSeq)
      )
    )
  }
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

  def toJson: JsValue = {
    import Entity._
    import IsadG._
    import DocumentaryUnitF._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        TITLE -> title,
        LANG_CODE -> languageCode,
        EXTENT_MEDIUM -> extentAndMedium,
        ADMIN_BIOG -> context.adminBiogHistory,
        ARCH_HIST -> context.archivalHistory,
        ACQUISITION -> context.acquisition,
        SCOPE_CONTENT -> content.scopeAndContent,
        APPRAISAL -> content.appraisal,
        ACCRUALS -> content.accruals,
        SYS_ARR -> content.systemOfArrangement,
        ACCESS_COND -> conditions.conditionsOfAccess,
        REPROD_COND -> conditions.conditionsOfReproduction,
        LANG_MATERIALS -> conditions.languageOfMaterials,
        SCRIPT_MATERIALS -> conditions.scriptOfMaterials,
        PHYSICAL_CHARS -> conditions.physicalCharacteristics,
        FINDING_AIDS -> conditions.findingAids,
        LOCATION_ORIGINALS -> materials.locationOfOriginals,
        LOCATION_COPIES -> materials.locationOfCopies,
        RELATED_UNITS -> materials.relatedUnitsOfDescription,
        PUBLICATION_NOTE -> materials.publicationNote,
        ARCHIVIST_NOTE -> control.archivistNote,
        RULES_CONVENTIONS -> control.rulesAndConventions,
        DATES_DESCRIPTIONS -> control.datesOfDescriptions
      ),
      RELATIONSHIPS -> Json.obj(
        TemporalEntity.DATE_REL -> Json.toJson(dates.map(_.toJson).toSeq)
      )
    )
  }
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

  def formable: DocumentaryUnitF = new DocumentaryUnitF(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    copyrightStatus = copyrightStatus,
    scope = scope,
    descriptions = descriptions.map(_.formable)
  )
}

case class DocumentaryUnitDescription(val e: Entity)
  extends Description
  with TemporalEntity
  with Formable[DocumentaryUnitDescriptionF] {

  // This should have one logical object
  val item: Option[DocumentaryUnit] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(DocumentaryUnit(_))

  import models.IsadG._
  import DocumentaryUnitDescriptionF._

  def formable = new DocumentaryUnitDescriptionF(
    id = Some(e.id),
    languageCode = languageCode,
    title = stringProperty(TITLE),
    dates = dates.map(_.formable),
    extentAndMedium = stringProperty(EXTENT_MEDIUM),
    context = Context(
      adminBiogHistory = stringProperty(ADMIN_BIOG),
      archivalHistory = stringProperty(ARCH_HIST),
      acquisition = stringProperty(ACQUISITION)
    ),
    content = Content(
      scopeAndContent = stringProperty(SCOPE_CONTENT),
      appraisal = stringProperty(APPRAISAL),
      accruals = stringProperty(ACCRUALS),
      systemOfArrangement = stringProperty(SYS_ARR)
    ),
    conditions = Conditions(
      conditionsOfAccess = stringProperty(ACCESS_COND),
      conditionsOfReproduction = stringProperty(REPROD_COND),
      languageOfMaterials = listProperty(LANG_MATERIALS),
      scriptOfMaterials = listProperty(SCRIPT_MATERIALS),
      physicalCharacteristics = stringProperty(PHYSICAL_CHARS),
      findingAids = stringProperty(FINDING_AIDS)
    ),
    materials = Materials(
      locationOfOriginals = stringProperty(LOCATION_ORIGINALS),
      locationOfCopies = stringProperty(LOCATION_COPIES),
      relatedUnitsOfDescription = stringProperty(RELATED_UNITS),
      publicationNote = stringProperty(PUBLICATION_NOTE)
    ),
    control = Control(
      archivistNote = stringProperty(ARCHIVIST_NOTE),
      rulesAndConventions = stringProperty(RULES_CONVENTIONS),
      datesOfDescriptions = stringProperty(DATES_DESCRIPTIONS)
    )
  )
}
