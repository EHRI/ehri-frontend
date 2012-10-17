package models

import defines._

object DocumentaryUnit {
  def apply(e: AccessibleEntity) = {
    new DocumentaryUnit(
      identifier = e.identifier,
      publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt),
      data = e.valueData
    )
  }
}

case class DocumentaryUnit(
  val identifier: String,
  val publicationStatus: Option[PublicationStatus.Status] = None,
  val data: Map[String, Any] = Map()) {

}
