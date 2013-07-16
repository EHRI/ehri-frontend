package models

import models.base._
import play.api.libs.json.{JsObject, JsValue, Json}
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}

private[models] case class IsdiahDetails(
  history: Option[String] = None,
  generalContext: Option[String] = None,
  mandates: Option[String] = None,
  administrativeStructure: Option[String] = None,
  records: Option[String] = None,
  buildings: Option[String] = None,
  holdings: Option[String] = None,
  findingAids: Option[String] = None
) extends AttributeSet

private[models] case class IsdiahAccess(
  openingTimes: Option[String] = None,
  conditions: Option[String] = None,
  accessibility: Option[String] = None
) extends AttributeSet

private[models] case class IsdiahServices(
  researchServices: Option[String] = None,
  reproductionServices: Option[String] = None,
  publicAreas: Option[String] = None
) extends AttributeSet

private[models] case class IsdiahControl(
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
    val restFormat = models.json.IsdiahFormat.restFormat

    private implicit val entityFormat = json.entityFormat
    private implicit val addressFormat = AddressF.Converter.clientFormat
    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    private implicit val isdiahDetailsFormat = Json.format[IsdiahDetails]
    private implicit val isdiahAccessFormat = Json.format[IsdiahAccess]
    private implicit val isdiahServicesFormat = Json.format[IsdiahServices]
    private implicit val isdiahControlFormat = Json.format[IsdiahControl]
    val clientFormat = Json.format[RepositoryDescriptionF]
  }
}


case class RepositoryDescriptionF(
  isA: EntityType.Value = EntityType.RepositoryDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(RepositoryF.ADDRESS_REL) addresses: List[AddressF] = Nil,
  details: IsdiahDetails,
  access: IsdiahAccess,
  services: IsdiahServices,
  control: IsdiahControl,
  accessPoints: List[AccessPointF] = Nil,
  unknownProperties: List[Entity] = Nil
) extends Model with Persistable with Description

