package models

import defines._
import models.base.HierarchicalEntity
import models.base.AccessibleEntity
import models.base.Accessor
import models.base.NamedEntity
import models.base.Description
import models.base.DescribedEntity
import models.base.Formable
import models.base.Persistable
import models.base.AttributeSet
import models.base.Field
import models.base.Field._
import models.base.TemporalEntity

case class DocumentaryUnitRepr(val e: Entity) extends NamedEntity
  with AccessibleEntity
  with HierarchicalEntity
  with DescribedEntity
  with TemporalEntity
  with Formable[DocumentaryUnit] {

  import DocumentaryUnit._
  import DescribedEntity._

  val holder: Option[AgentRepr] = e.relations(HELD_REL).headOption.map(AgentRepr(_))
  val parent: Option[DocumentaryUnitRepr] = e.relations(CHILD_REL).headOption.map(DocumentaryUnitRepr(_))
  val publicationStatus = e.property(PUB_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  override def descriptions: List[DocumentaryUnitDescriptionRepr] = e.relations(DESCRIBES_REL).map(DocumentaryUnitDescriptionRepr(_))

  def to: DocumentaryUnit = new DocumentaryUnit(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.to)
  )
}

case class DocumentaryUnitDescriptionRepr(val e: Entity)
  extends Description
  with Formable[DocumentaryUnitDescription] {
  import DocumentaryUnit._
  import DocumentaryUnitDescription._
  def to = new DocumentaryUnitDescription(
    id = Some(e.id),
    languageCode = languageCode,
    title = e.property(TITLE).flatMap(_.asOpt[String]),
    context = Context(
      adminBiogHist = stringProperty(ADMIN_BIOG),
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
      physicalCharacteristics = stringProperty(PHYSICAL_CHARS),
      findingAids = stringProperty(FINDING_AIDS)
    ),
    materials = Materials(
      locationOfOriginals = stringProperty(LOCATION_ORIGINALS),
      locationOfCopies = stringProperty(LOCATION_COPIES),
      relatedUnitsOfDescription = stringProperty(RELATED_UNITS),
      publicationNote = stringProperty(PUBLICATION_NOTE)
    )
  )
}

object DocumentaryUnit {

  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "heldBy"
  final val CHILD_REL = "childOf"

  /* ISAD(G)-based field set */
  val NAME = Field("name", "Name")
  val TITLE = Field("title", "Title")
  val ADMIN_BIOG = Field("adminBiogHist", "Administrator and Biographical History")
  val ARCH_HIST = Field("archivalHistory", "Archival History")
  val ACQUISITION = Field("acquisition", "Acquisition")
  val SCOPE_CONTENT = Field("scopeAndContent", "Scope and Content")
  val APPRAISAL = Field("appraisal", "Appraisals")
  val ACCRUALS = Field("accruals", "Accruals")
  val SYS_ARR = Field("systemOfArrangement", "System of Arrangement")
  val PUB_STATUS = Field("publicationStatus", "Publication Status")
  val ACCESS_COND = Field("conditionsOfAccess", "Conditions Governing Access")
  val REPROD_COND = Field("conditionsOfReproduction", "Conditions Governing Reproduction")
  val PHYSICAL_CHARS = Field("physicalCharacteristics", "Physical Characteristics")
  val FINDING_AIDS = Field("findingAids", "Finding Aids")
  val LOCATION_ORIGINALS = Field("locationOfOriginals", "Location of Originals")
  val LOCATION_COPIES = Field("locationOfCopies", "Location of Copies")
  val RELATED_UNITS = Field("relatedUnitsOfDescription", "Related Units of Description")
  val PUBLICATION_NOTE = Field("publicationNote", "Publication Note")
}

case class DocumentaryUnit(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,

  @Annotations.Relation(DocumentaryUnit.DESC_REL) val descriptions: List[DocumentaryUnitDescription] = Nil) extends Persistable {
  val isA = EntityType.DocumentaryUnit

  def withDescription(d: DocumentaryUnitDescription): DocumentaryUnit = copy(descriptions = descriptions ++ List(d))
}

case class DocumentaryUnitDescription(
  val id: Option[String],
  val languageCode: String,
  val title: Option[String] = None,
  val context: DocumentaryUnitDescription.Context,
  val content: DocumentaryUnitDescription.Content,
  val conditions: DocumentaryUnitDescription.Conditions,
  val materials: DocumentaryUnitDescription.Materials
) extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription  
}

object DocumentaryUnitDescription {
	case class Context(
	  val adminBiogHist: Option[String] = None,
	  val archivalHistory: Option[String] = None,
	  val acquisition: Option[String] = None) extends AttributeSet
	
	case class Content(
	  val scopeAndContent: Option[String] = None,
	  val appraisal: Option[String] = None,
	  val accruals: Option[String] = None,
	  val systemOfArrangement: Option[String] = None) extends AttributeSet
	
	case class Conditions(
	  val conditionsOfAccess: Option[String] = None,
	  val conditionsOfReproduction: Option[String] = None,
	  val physicalCharacteristics: Option[String] = None,
	  val findingAids: Option[String] = None) extends AttributeSet
	
	case class Materials(
	  val locationOfOriginals: Option[String] = None,
	  val locationOfCopies: Option[String] = None,
	  val relatedUnitsOfDescription: Option[String] = None,
	  val publicationNote: Option[String] = None) extends AttributeSet    
}

