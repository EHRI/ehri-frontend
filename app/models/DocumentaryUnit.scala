package models

import defines._


object DocumentaryUnit {
  
  final val DESC_REL = "describes"
  
  def apply(e: AccessibleEntity) = {
    new DocumentaryUnit(
      id = Some(e.id),
      identifier = e.identifier,
      name = e.property("name").flatMap(_.asOpt[String]).getOrElse(""), // FIXME: Is this a good idea?
      publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt),
      descriptions = e.relations(DESC_REL).map(DocumentaryUnitDescription.apply(_))
    )
  }
}

case class DocumentaryUnit(
  val id: Option[Long],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  
  @Annotations.Relation(DocumentaryUnit.DESC_REL)
  val descriptions: List[DocumentaryUnitDescription] = Nil
) extends ManagedEntity {
  val isA = EntityTypes.DocumentaryUnit

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
  val isA = EntityTypes.DocumentaryUnitDescription
  
}
