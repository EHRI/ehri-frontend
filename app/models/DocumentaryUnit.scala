package models

import defines._




object DocumentaryUnit {
  
  val DESC_REL = "describes"
  
  def apply(e: AccessibleEntity) = {
    new DocumentaryUnit(
      identifier = e.identifier,
      publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt),
      descriptions = e.relations(DESC_REL).map(DocumentaryUnitDescription.apply(_))
    )
  }
}

case class DocumentaryUnit(
  val identifier: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  val descriptions: List[DocumentaryUnitDescription] = Nil) extends BaseModel {

  def withDescription(d: DocumentaryUnitDescription): DocumentaryUnit = copy(descriptions=descriptions++List(d))
  
  def this(identifier: String, publicationStatus: Option[PublicationStatus.Value]) = 
    this(identifier, publicationStatus, Nil)
}

object DocumentaryUnitDescription {
  def apply(e: Entity) = {
    new DocumentaryUnitDescription(
      languageCode = e.property("languageCode").map(_.as[String]).getOrElse(""),
      title = e.property("title").flatMap(_.asOpt[String]),
      scopeAndContent = e.property("scopeAndContent").flatMap(_.asOpt[String])
    )
  }
}

case class DocumentaryUnitDescription(
  val languageCode: String,
  val title: Option[String],
  val scopeAndContent: Option[String]
)  extends BaseModel {
  
}
