package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus, enum}
import base._

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._
import defines.EnumWriter.enumWrites



/**
 * ISDIAH Field definitions
 */
case object Isdiah {
  val IDENTIFIER = "identifier"
  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"

  val LANG_CODE = "languageCode"

  // Field set
  val IDENTITY_AREA = "identityArea"
  val AUTHORIZED_FORM_OF_NAME = "authorizedFormOfName"
  val OTHER_FORMS_OF_NAME = "otherFormsOfName"
  val PARALLEL_FORMS_OF_NAME = "parallelFormsOfName"
  val INSTITUTION_TYPE = "institutionType"

  // AddressF
  val ADDRESS_AREA = "addressArea"
  val ADDRESS_NAME = "addressName"
  val CONTACT_PERSON = "contactPerson"
  val STREET_ADDRESS = "streetAddress"
  val CITY = "city"
  val REGION = "region"
  val COUNTRY_CODE = "countryCode"
  val EMAIL = "email"
  val TELEPHONE = "telephone"
  val FAX = "fax"
  val URL = "url"

  val DESCRIPTION_AREA = "descriptionArea"
  val HISTORY = "history"
  val GENERAL_CONTEXT = "generalContext"
  val MANDATES = "mandates"
  val ADMINISTRATIVE_STRUCTURE = "administrativeStructure"
  val RECORDS = "records"
  val BUILDINGS = "buildings"
  val HOLDINGS = "holdings"
  val FINDING_AIDS = "findingAids"

  // Access
  val ACCESS_AREA = "accessArea"
  val OPENING_TIMES = "openingTimes"
  val CONDITIONS = "conditions"
  val ACCESSIBILITY = "accessibility"

  // Services
  val SERVICES_AREA = "servicesArea"
  val RESEARCH_SERVICES = "researchServices"
  val REPROD_SERVICES = "reproductionServices"
  val PUBLIC_AREAS = "publicAreas"

  // Control
  val CONTROL_AREA = "controlArea"
  val DESCRIPTION_IDENTIFIER = "descriptionIdentifier"
  val INSTITUTION_IDENTIFIER = "institutionIdentifier"
  val RULES_CONVENTIONS = "rulesAndConventions"
  val STATUS = "status"
  val LEVEL_OF_DETAIL = "levelOfDetail"
  val DATES_CVD = "datesCVD"
  val LANGUAGES_USED = "languages"
  val SCRIPTS_USED = "scripts"
  val SOURCES = "sources"
  val MAINTENANCE_NOTES = "maintenanceNotes"
}


object RepositoryF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"

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

  def toJson: JsValue = {
    import RepositoryF._
    import Isdiah._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        IDENTIFIER -> identifier,
        NAME -> name,
        PUBLICATION_STATUS -> publicationStatus,
        RepositoryF.PRIORITY -> priority
      ),
      Entity.RELATIONSHIPS -> Json.obj(
        DESC_REL -> Json.toJson(descriptions.map(_.toJson).toSeq)
      )
    )
  }

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

  def toJson: JsValue = {
    import AddressF._
    import Entity._
    import Isdiah._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        AUTHORIZED_FORM_OF_NAME -> name,
        LANG_CODE -> languageCode,
        OTHER_FORMS_OF_NAME -> otherFormsOfName,
        PARALLEL_FORMS_OF_NAME -> parallelFormsOfName,
        HISTORY -> details.history,
        GENERAL_CONTEXT -> details.generalContext,
        MANDATES -> details.mandates,
        ADMINISTRATIVE_STRUCTURE -> details.administrativeStructure,
        RECORDS -> details.records,
        BUILDINGS -> details.buildings,
        HOLDINGS -> details.holdings,
        FINDING_AIDS -> details.findingAids,
        OPENING_TIMES -> access.openingTimes,
        CONDITIONS -> access.conditions,
        ACCESSIBILITY -> access.accessibility,
        RESEARCH_SERVICES -> services.researchServices,
        REPROD_SERVICES -> services.reproductionServices,
        PUBLIC_AREAS -> services.publicAreas,
        DESCRIPTION_IDENTIFIER -> control.descriptionIdentifier,
        INSTITUTION_IDENTIFIER -> control.institutionIdentifier,
        RULES_CONVENTIONS -> control.rulesAndConventions,
        STATUS -> control.status,
        LEVEL_OF_DETAIL -> control.levelOfDetail,
        DATES_CVD -> control.datesCDR,
        LANGUAGES_USED -> control.languages,
        SCRIPTS_USED -> control.scripts,
        MAINTENANCE_NOTES -> control.maintenanceNotes
      ),
      RELATIONSHIPS -> Json.obj(
        RepositoryF.ADDRESS_REL -> Json.toJson(addresses.map(_.toJson).toSeq)
      )
    )
  }
}

object AddressF {

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
) extends Persistable {
  val isA = EntityType.Address

  def toJson: JsValue = {
    import Entity._
    import Isdiah._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        ADDRESS_NAME -> name,
        CONTACT_PERSON -> contactPerson,
        STREET_ADDRESS -> streetAddress,
        CITY -> city,
        REGION -> region,
        COUNTRY_CODE -> countryCode,
        EMAIL -> email,
        TELEPHONE -> telephone,
        FAX -> fax,
        URL -> url
      )
    )
  }
}


object RepositoryDescriptionForm {

  import RepositoryDescriptionF._
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      AUTHORIZED_FORM_OF_NAME -> optional(text),
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      ADDRESS_AREA -> list(
        mapping(
          Entity.ID -> optional(nonEmptyText),
          ADDRESS_NAME -> nonEmptyText,
          CONTACT_PERSON -> optional(text),
          STREET_ADDRESS -> optional(text),
          CITY -> optional(text),
          REGION -> optional(text),
          COUNTRY_CODE -> optional(text),
          EMAIL -> optional(email),
          TELEPHONE -> optional(text),
          FAX -> optional(text),
          URL -> optional(text)
        )(AddressF.apply)(AddressF.unapply)
      ),
      DESCRIPTION_AREA -> mapping(
        HISTORY -> optional(text),
        GENERAL_CONTEXT -> optional(text),
        MANDATES -> optional(text),
        ADMINISTRATIVE_STRUCTURE -> optional(text),
        RECORDS -> optional(text),
        BUILDINGS -> optional(text),
        HOLDINGS -> optional(text),
        FINDING_AIDS -> optional(text)
      )(Details.apply)(Details.unapply),
      ACCESS_AREA -> mapping(
        OPENING_TIMES -> optional(text),
        CONDITIONS -> optional(text),
        ACCESSIBILITY -> optional(text)
      )(Access.apply)(Access.unapply),
      SERVICES_AREA -> mapping(
        RESEARCH_SERVICES -> optional(text),
        REPROD_SERVICES -> optional(text),
        PUBLIC_AREAS -> optional(text)
      )(Services.apply)(Services.unapply),
      CONTROL_AREA -> mapping(
        DESCRIPTION_IDENTIFIER -> optional(text),
        INSTITUTION_IDENTIFIER -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        STATUS -> optional(text),
        LEVEL_OF_DETAIL -> optional(text),
        DATES_CVD -> optional(text),
        LANGUAGES_USED -> optional(list(nonEmptyText)),
        SCRIPTS_USED -> optional(list(nonEmptyText)),
        SOURCES -> optional(text),
        MAINTENANCE_NOTES -> optional(text)
      )(Control.apply)(Control.unapply)
    )(RepositoryDescriptionF.apply)(RepositoryDescriptionF.unapply)
  )
}

object RepositoryForm {

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      Isdiah.NAME -> nonEmptyText,
      Isdiah.PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      DescribedEntity.DESCRIPTIONS -> list(RepositoryDescriptionForm.form.mapping),
      RepositoryF.PRIORITY -> optional(number(min = -1, max = 5))
    )(RepositoryF.apply)(RepositoryF.unapply)
  )
}


case class Repository(val e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with DescribedEntity
  with Formable[RepositoryF] {
  override def descriptions: List[RepositoryDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(RepositoryDescription(_))

  // Shortcuts...
  val publicationStatus = e.property(Isdiah.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  val priority = e.property(RepositoryF.PRIORITY).flatMap(_.asOpt[Int])

  def formable: RepositoryF = new RepositoryF(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.formable),
    priority = priority
  )
}

case class RepositoryDescription(val e: Entity) extends Description with Formable[RepositoryDescriptionF] {

  import RepositoryDescriptionF._
  import Isdiah._

  lazy val item: Option[Repository] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Repository(_))
  def addresses: List[Address] = e.relations(RepositoryF.ADDRESS_REL).map(Address(_))

  def formable: RepositoryDescriptionF = new RepositoryDescriptionF(
    id = Some(e.id),
    languageCode = languageCode,
    name = e.stringProperty(AUTHORIZED_FORM_OF_NAME),
    otherFormsOfName = e.listProperty(OTHER_FORMS_OF_NAME),
    parallelFormsOfName = e.listProperty(PARALLEL_FORMS_OF_NAME),
    addresses = addresses.map(_.formable),
    details = Details(
      history = e.stringProperty(HISTORY),
      generalContext = e.stringProperty(GENERAL_CONTEXT),
      mandates = e.stringProperty(MANDATES),
      administrativeStructure = e.stringProperty(ADMINISTRATIVE_STRUCTURE),
      records = e.stringProperty(RECORDS),
      buildings = e.stringProperty(BUILDINGS),
      holdings = e.stringProperty(HOLDINGS),
      findingAids = e.stringProperty(FINDING_AIDS)
    ),
    access = Access(
      openingTimes = e.stringProperty(OPENING_TIMES),
      conditions = e.stringProperty(CONDITIONS),
      accessibility = e.stringProperty(ACCESSIBILITY)
    ),
    services = Services(
      researchServices = e.stringProperty(RESEARCH_SERVICES),
      reproductionServices = e.stringProperty(REPROD_SERVICES),
      publicAreas = e.stringProperty(PUBLIC_AREAS)
    ),
    control = Control(
      descriptionIdentifier = e.stringProperty(DESCRIPTION_IDENTIFIER),
      institutionIdentifier = e.stringProperty(INSTITUTION_IDENTIFIER),
      rulesAndConventions = e.stringProperty(RULES_CONVENTIONS),
      status = e.stringProperty(STATUS),
      levelOfDetail = e.stringProperty(LEVEL_OF_DETAIL),
      datesCDR = e.stringProperty(DATES_CVD),
      languages = e.listProperty(LANGUAGES_USED),
      scripts = e.listProperty(SCRIPTS_USED),
      sources = e.stringProperty(SOURCES),
      maintenanceNotes = e.stringProperty(MAINTENANCE_NOTES)
    )
  )
}

case class Address(val e: Entity) extends AccessibleEntity with Formable[AddressF] {

  import Isdiah._

  def formable: AddressF = new AddressF(
    id = Some(e.id),
    name = e.stringProperty(ADDRESS_NAME).getOrElse("Unnamed Address"),
    contactPerson = e.stringProperty(CONTACT_PERSON),
    streetAddress = e.stringProperty(STREET_ADDRESS),
    city = e.stringProperty(CITY),
    region = e.stringProperty(REGION),
    countryCode = e.stringProperty(COUNTRY_CODE),
    email = e.stringProperty(EMAIL),
    telephone = e.stringProperty(TELEPHONE),
    fax = e.stringProperty(FAX),
    url = e.stringProperty(URL)
  )
}

