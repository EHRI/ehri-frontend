package models

import defines._

object DocumentaryUnit extends ManagedEntityBuilder[DocumentaryUnit] {
  
  final val DESC_REL = "describes"
  final val ACCESS_REL = "access"
  final val HELD_REL = "holds"
  
  def apply(e: AccessibleEntity) = {
    new DocumentaryUnit(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse(""), // FIXME: Is this a good idea?
      publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt),
      descriptions = e.relations(DESC_REL).map(DocumentaryUnitDescription.apply(_)),
      holder = e.relations(HELD_REL).headOption.map(e => Agent.apply(new AccessibleEntity(e))),
      accessors = e.relations(ACCESS_REL).flatMap { e =>
        e.property("isA").flatMap(_.asOpt[String]).map { s => s match {
          case s: String if s == EntityType.UserProfile.toString => List(UserProfile.apply(new AccessibleEntity(e)))
          case s: String if s == EntityType.Group.toString => List(Group.apply(new AccessibleEntity(e)))
          case _ => Nil
        }}        
      }.flatten
    )
  }
  
  def apply(id: Option[Long], identifier: String, name: String, publicationStatus: Option[PublicationStatus.Value],
		  	descriptions: List[DocumentaryUnitDescription])
  		= new DocumentaryUnit(id, identifier, name, publicationStatus, descriptions, None, Nil)

  // Special form unapply method
  def unform(d: DocumentaryUnit) = Some((d.id, d.identifier, d.name, d.publicationStatus, d.descriptions))
  
}

case class DocumentaryUnit(
  val id: Option[Long],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  
  @Annotations.Relation(DocumentaryUnit.DESC_REL)
  val descriptions: List[DocumentaryUnitDescription] = Nil,
  @Annotations.Relation(DocumentaryUnit.HELD_REL)
  val holder: Option[Agent] = None,
  @Annotations.Relation(DocumentaryUnit.ACCESS_REL)
  val accessors: List[Accessor] = Nil
) extends ManagedEntity {
  val isA = EntityType.DocumentaryUnit

  def withDescription(d: DocumentaryUnitDescription): DocumentaryUnit = copy(descriptions=descriptions++List(d))
  
  def this(identifier: String, name: String, publicationStatus: Option[PublicationStatus.Value]) = 
    this(None, identifier, name, publicationStatus, Nil)
}

object DocumentaryUnitDescription {
  def apply(e: Entity) = {
    new DocumentaryUnitDescription(
      id = Some(e.id),
      languageCode = e.property("languageCode").map(_.as[String]).getOrElse(""),
      title = e.property("title").flatMap(_.asOpt[String]),
      scopeAndContent = e.property("scopeAndContent").flatMap(_.asOpt[String])
    )
  }
}

case class DocumentaryUnitDescription(
  val id: Option[Long],
  val languageCode: String,
  val title: Option[String] = None,
  val scopeAndContent: Option[String] = None
)  extends BaseModel {
  val isA = EntityType.DocumentaryUnitDescription
  
}
