package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType

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

  lazy implicit val repositoryDescriptionFormat = json.IsdiahFormat.isdiahFormat
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

  def toJson: JsValue = Json.toJson(this)
}


case class RepositoryDescription(val e: Entity) extends Description with Formable[RepositoryDescriptionF] {

  import RepositoryDescriptionF._
  import Isdiah._

  lazy val item: Option[Repository] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Repository(_))
  def addresses: List[Address] = e.relations(RepositoryF.ADDRESS_REL).map(Address(_))

  lazy val formable: RepositoryDescriptionF = Json.toJson(e).as[RepositoryDescriptionF]
  lazy val formableOpt: Option[RepositoryDescriptionF] = Json.toJson(e).asOpt[RepositoryDescriptionF]
}