package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}

case class IsdiahDetails(
  history: Option[String] = None,
  generalContext: Option[String] = None,
  mandates: Option[String] = None,
  administrativeStructure: Option[String] = None,
  records: Option[String] = None,
  buildings: Option[String] = None,
  holdings: Option[String] = None,
  findingAids: Option[String] = None
) extends AttributeSet

case class IsdiahAccess(
  openingTimes: Option[String] = None,
  conditions: Option[String] = None,
  accessibility: Option[String] = None
) extends AttributeSet

case class IsdiahServices(
  researchServices: Option[String] = None,
  reproductionServices: Option[String] = None,
  publicAreas: Option[String] = None
) extends AttributeSet

case class IsdiahControl(
  descriptionIdentifier: Option[String] = None,
  institutionIdentifier: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  status: Option[String] = None,
  levelOfDetail: Option[String] = None,
  datesCDR: Option[String] = None,
  languages: Option[List[String]] = None,
  scripts: Option[List[String]] = None,
  sources: Option[List[String]] = None,
  maintenanceNotes: Option[String] = None
) extends AttributeSet

object RepositoryDescriptionF {

  implicit object Converter extends RestConvertable[RepositoryDescriptionF] with ClientConvertable[RepositoryDescriptionF] {
    lazy val restFormat = models.json.rest.isdiahFormat
    lazy val clientFormat = models.json.client.isdiahFormat
  }
}


case class RepositoryDescriptionF(
  id: Option[String],
  languageCode: String,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(RepositoryF.ADDRESS_REL) addresses: List[AddressF] = Nil,
  details: IsdiahDetails,
  access: IsdiahAccess,
  services: IsdiahServices,
  control: IsdiahControl
  ) extends Persistable {
  val isA = EntityType.RepositoryDescription
}


case class RepositoryDescription(val e: Entity) extends Description with Formable[RepositoryDescriptionF] {

  import RepositoryDescriptionF._
  import Isdiah._

  lazy val item: Option[Repository] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Repository(_))
  def addresses: List[Address] = e.relations(RepositoryF.ADDRESS_REL).map(Address(_))

  lazy val formable: RepositoryDescriptionF = Json.toJson(e).as[RepositoryDescriptionF](json.IsdiahFormat.restFormat)
  lazy val formableOpt: Option[RepositoryDescriptionF] = Json.toJson(e).asOpt[RepositoryDescriptionF](json.IsdiahFormat.restFormat)
}