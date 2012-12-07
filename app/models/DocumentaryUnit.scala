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
  val holder: Option[AgentRepr] = e.relations(DocumentaryUnit.HELD_REL).headOption.map(AgentRepr(_))
  val parent: Option[DocumentaryUnitRepr] = e.relations(DocumentaryUnit.CHILD_REL).headOption.map(DocumentaryUnitRepr(_))
  val publicationStatus = e.property(DocumentaryUnit.PUB_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  override def descriptions: List[DocumentaryUnitDescriptionRepr] = e.relations(DescribedEntity.DESCRIBES_REL).map(DocumentaryUnitDescriptionRepr(_))
  
  def to: DocumentaryUnit = new DocumentaryUnit(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.to)
  )
}

case class DocumentaryUnitDescriptionRepr(val e: Entity) extends Description with Formable[DocumentaryUnitDescription] {
  def to = new DocumentaryUnitDescription(
	id = Some(e.id),
	languageCode = languageCode,
	title = e.property(DocumentaryUnit.TITLE).flatMap(_.asOpt[String]),
	context = DocumentaryUnitContext(
	  adminBiogHist = stringProperty(DocumentaryUnit.ADMIN_BIOG),
	  archivalHistory = stringProperty(DocumentaryUnit.ARCH_HIST),
	  acquisition = stringProperty(DocumentaryUnit.ACQUISITION)	  
	),
	content = DocumentaryUnitContent(
		scopeAndContent = stringProperty(DocumentaryUnit.SCOPE_CONTENT),    
		appraisal = stringProperty(DocumentaryUnit.APPRAISAL),
		accruals = stringProperty(DocumentaryUnit.ACCRUALS),
		systemOfArrangement = stringProperty(DocumentaryUnit.SYS_ARR)
	),
	conditions = DocumentaryUnitConditions(
		conditionsOfAccess = stringProperty(DocumentaryUnit.ACCESS_COND),
		conditionsOfReproduction = stringProperty(DocumentaryUnit.REPROD_COND),
		physicalCharacteristics = stringProperty(DocumentaryUnit.PHYSICAL_CHARS),
		findingAids = stringProperty(DocumentaryUnit.FINDING_AIDS)
	),
	materials = DocumentaryUnitMaterials(
		locationOfOriginals = stringProperty(DocumentaryUnit.LOCATION_ORIGINALS),
		locationOfCopies = stringProperty(DocumentaryUnit.LOCATION_COPIES),
		relatedUnitsOfDescription = stringProperty(DocumentaryUnit.RELATED_UNITS),
		publicationNote = stringProperty(DocumentaryUnit.PUBLICATION_NOTE)
	)	
  )
}

object DocumentaryUnit  {
  
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
  
  @Annotations.Relation(DocumentaryUnit.DESC_REL)
  val descriptions: List[DocumentaryUnitDescription] = Nil
) extends Persistable {
  val isA = EntityType.DocumentaryUnit

  def withDescription(d: DocumentaryUnitDescription): DocumentaryUnit = copy(descriptions=descriptions++List(d))
}

case class DocumentaryUnitDescription(
  val id: Option[String],
  val languageCode: String,
  val title: Option[String] = None,
  val context: DocumentaryUnitContext,
  val content: DocumentaryUnitContent,
  val conditions: DocumentaryUnitConditions,
  val materials: DocumentaryUnitMaterials
)  extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription
  
}

case class DocumentaryUnitContext(
		val adminBiogHist: Option[String] = None,
		val archivalHistory: Option[String] = None,
		val acquisition: Option[String] = None
) extends AttributeSet

case class DocumentaryUnitContent(
		val scopeAndContent: Option[String] = None,
		val appraisal: Option[String] = None,
		val accruals: Option[String] = None,
		val systemOfArrangement: Option[String] = None
) extends AttributeSet

case class DocumentaryUnitConditions(
		val conditionsOfAccess: Option[String] = None,
		val conditionsOfReproduction: Option[String] = None,
		val physicalCharacteristics: Option[String] = None,
		val findingAids: Option[String] = None
) extends AttributeSet

case class DocumentaryUnitMaterials(
		val locationOfOriginals: Option[String] = None,
		val locationOfCopies: Option[String] = None,
		val relatedUnitsOfDescription: Option[String] = None,
		val publicationNote: Option[String] = None
) extends AttributeSet
