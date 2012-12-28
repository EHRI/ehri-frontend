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
import models.base.TemporalEntity

case object IsadG {
  /* ISAD(G)-based field set */
  val NAME = "name"
  val IDENTIFIER = "identifier"
  val TITLE = "title"
  val ADMIN_BIOG = "adminBiogHist"
  val ARCH_HIST = "archivalHistory"
  val ACQUISITION = "acquisition"
  val SCOPE_CONTENT = "scopeAndContent"
  val APPRAISAL = "appraisal"
  val ACCRUALS = "accruals"
  val SYS_ARR = "systemOfArrangement"
  val PUB_STATUS = "publicationStatus"
  val ACCESS_COND = "conditionsOfAccess"
  val REPROD_COND = "conditionsOfReproduction"
  val PHYSICAL_CHARS = "physicalCharacteristics"
  val FINDING_AIDS = "findingAids"
  val LOCATION_ORIGINALS = "locationOfOriginals"
  val LOCATION_COPIES = "locationOfCopies"
  val RELATED_UNITS = "relatedUnitsOfDescription"
  val PUBLICATION_NOTE = "publicationNote"

}

case class DocumentaryUnitRepr(val e: Entity) extends NamedEntity
  with AccessibleEntity
  with HierarchicalEntity
  with DescribedEntity
  with TemporalEntity
  with Formable[DocumentaryUnit] {

  import DocumentaryUnit._
  import DescribedEntity._
  import IsadG._

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
  import IsadG._
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

