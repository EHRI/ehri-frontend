package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus, enum}
import base._

import play.api.libs.json._
import defines.EnumWriter.enumWrites


object RepositoryF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"

  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"
  final val PRIORITY = "priority"

}

case class RepositoryF(
  id: Option[String],
  identifier: String,
  name: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(RepositoryF.DESC_REL) descriptions: List[RepositoryDescriptionF] = Nil,
  priority: Option[Int] = None
) extends Persistable {
  val isA = EntityType.Repository

  import json.RepositoryFormat._
  def toJson: JsValue = Json.toJson(this)
}

object RepositoryDescriptionF {

  case class Details(
    history: Option[String] = None,
    generalContext: Option[String] = None,
    mandates: Option[String] = None,
    administrativeStructure: Option[String] = None,
    records: Option[String] = None,
    buildings: Option[String] = None,
    holdings: Option[String] = None,
    findingAids: Option[String] = None
  ) extends AttributeSet

  case class Access(
    openingTimes: Option[String] = None,
    conditions: Option[String] = None,
    accessibility: Option[String] = None
  ) extends AttributeSet

  case class Services(
    researchServices: Option[String] = None,
    reproductionServices: Option[String] = None,
    publicAreas: Option[String] = None
  ) extends AttributeSet

  case class Control(
    descriptionIdentifier: Option[String] = None,
    institutionIdentifier: Option[String] = None,
    rulesAndConventions: Option[String] = None,
    status: Option[String] = None,
    levelOfDetail: Option[String] = None,
    datesCDR: Option[String] = None,
    languages: Option[List[String]] = None,
    scripts: Option[List[String]] = None,
    sources: Option[String] = None,
    maintenanceNotes: Option[String] = None
  ) extends AttributeSet

}

case class RepositoryDescriptionF(
  id: Option[String],
  languageCode: String,
  name: Option[String] = None,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(RepositoryF.ADDRESS_REL) addresses: List[AddressF] = Nil,
  details: RepositoryDescriptionF.Details,
  access: RepositoryDescriptionF.Access,
  services: RepositoryDescriptionF.Services,
  control: RepositoryDescriptionF.Control
) extends Persistable {
  val isA = EntityType.RepositoryDescription

  import json.IsdiahFormat._
  def toJson: JsValue = Json.toJson(this)
}

object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"
}

case class AddressF(
  id: Option[String],
  name: String,
  contactPerson: Option[String] = None,
  streetAddress: Option[String] = None,
  city: Option[String] = None,
  region: Option[String] = None,
  countryCode: Option[String] = None,
  email: Option[String] = None,
  telephone: Option[String] = None,
  fax: Option[String] = None,
  url: Option[String] = None
) {
  val isA = EntityType.Address
}


case class Repository(val e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with DescribedEntity
  with Formable[RepositoryF] {
  override def descriptions: List[RepositoryDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(RepositoryDescription(_))

  // Shortcuts...
  val publicationStatus = e.property(RepositoryF.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  val priority = e.property(RepositoryF.PRIORITY).flatMap(_.asOpt[Int])

  import json.RepositoryFormat._
  lazy val formable: RepositoryF = Json.toJson(e).as[RepositoryF]
}

case class RepositoryDescription(val e: Entity) extends Description with Formable[RepositoryDescriptionF] {

  import RepositoryDescriptionF._
  import Isdiah._

  lazy val item: Option[Repository] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Repository(_))
  def addresses: List[Address] = e.relations(RepositoryF.ADDRESS_REL).map(Address(_))

  import json.IsdiahFormat._
  lazy val formable: RepositoryDescriptionF = Json.toJson(e).as[RepositoryDescriptionF]
}

case class Address(val e: Entity) extends AccessibleEntity with Formable[AddressF] {
  import json.AddressFormat._
  lazy val formable: AddressF = Json.toJson(e).as[AddressF]
}

