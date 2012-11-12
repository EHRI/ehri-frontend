package models

import defines._
import models.base.HierarchicalEntity
import models.base.AccessibleEntity
import models.base.Accessor
import models.base.NamedEntity
import models.base.Description
import models.base.DescribedEntity
import models.base.Formable

case class DocumentaryUnitRepr(val e: Entity) extends NamedEntity 
		with AccessibleEntity
		with HierarchicalEntity
		with DescribedEntity
		with Formable[DocumentaryUnit] {
  val holder: Option[AgentRepr] = e.relations(DocumentaryUnit.HELD_REL).headOption.map(AgentRepr(_))
  val publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt)
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
	title = e.property("title").flatMap(_.asOpt[String]),
	scopeAndContent = e.property("scopeAndContent").flatMap(_.asOpt[String])
  )
}

object DocumentaryUnit  {
  
  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "holds"
  
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
  val scopeAndContent: Option[String] = None
)  extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription
  
}
