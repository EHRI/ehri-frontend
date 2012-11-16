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

case class DocumentaryUnitRepr(val e: Entity) extends NamedEntity 
		with AccessibleEntity
		with HierarchicalEntity
		with DescribedEntity
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
	  adminBiogHist = e.property(DocumentaryUnit.ADMIN_BIOG).flatMap(_.asOpt[String]),
	  archivalHistory = e.property(DocumentaryUnit.ARCH_HIST).flatMap(_.asOpt[String]),
	  acquisition = e.property(DocumentaryUnit.ACQUISITION).flatMap(_.asOpt[String])	  
	),
	scopeAndContent = e.property(DocumentaryUnit.SCOPE_CONTENT).flatMap(_.asOpt[String])
  )
}

object DocumentaryUnit  {
  
  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "holds"
  final val CHILD_REL = "childOf"
    
  val NAME = "name"
  val TITLE = "title"
  val ADMIN_BIOG = "adminBiogHist"
  val ARCH_HIST = "archivalHistory"
  val ACQUISITION = "acquisition"
  val SCOPE_CONTENT = "scopeAndContent"
  val PUB_STATUS = "publicationStatus"
    
  
}

case class DocumentaryUnit(
  val id: Option[Long],
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
  val id: Option[Long],
  val languageCode: String,
  val title: Option[String] = None,
  val context: DocumentaryUnitContext,
  val scopeAndContent: Option[String] = None
)  extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription
  
}


case class DocumentaryUnitContext(
		val adminBiogHist: Option[String] = None,
		val archivalHistory: Option[String] = None,
		val acquisition: Option[String] = None
) extends AttributeSet