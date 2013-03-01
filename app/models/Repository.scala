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
}

case class RepositoryF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(RepositoryF.DESC_REL) val descriptions: List[RepositoryDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Agent

  def toJson: JsValue = {
    import RepositoryF._
    import Isdiah._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        IDENTIFIER -> identifier,
        NAME -> name,
        PUBLICATION_STATUS -> publicationStatus
      ),
      Entity.RELATIONSHIPS -> Json.obj(
        DESC_REL -> Json.toJson(descriptions.map(_.toJson).toSeq)
      )
    )
  }

}

object RepositoryDescriptionF {

  case class Details(
    val history: Option[String] = None,
    val generalContext: Option[String] = None,
    val mandates: Option[String] = None,
    val administrativeStructure: Option[String] = None,
    val records: Option[String] = None,
    val buildings: Option[String] = None,
    val holdings: Option[String] = None,
    val findingAids: Option[String] = None
  ) extends AttributeSet

  case class Access(
    val openingTimes: Option[String] = None,
    val conditions: Option[String] = None,
    val accessibility: Option[String] = None
  ) extends AttributeSet

  case class Services(
    val researchServices: Option[String] = None,
    val reproductionServices: Option[String] = None,
    val publicAreas: Option[String] = None
  ) extends AttributeSet

  case class Control(
    val descriptionIdentifier: Option[String] = None,
    val institutionIdentifier: Option[String] = None,
    val rulesAndConventions: Option[String] = None,
    val status: Option[String] = None,
    val levelOfDetail: Option[String] = None,
    val datesCDR: Option[String] = None,
    val languages: Option[List[String]] = None,
    val scripts: Option[List[String]] = None,
    val sources: Option[String] = None,
    val maintenanceNotes: Option[String] = None
  ) extends AttributeSet

}

case class RepositoryDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val name: Option[String] = None,
  val otherFormsOfName: Option[List[String]] = None,
  val parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(RepositoryF.ADDRESS_REL) val addresses: List[AddressF] = Nil,
  val details: RepositoryDescriptionF.Details,
  val access: RepositoryDescriptionF.Access,
  val services: RepositoryDescriptionF.Services,
  val control: RepositoryDescriptionF.Control
) extends Persistable {
  val isA = EntityType.AgentDescription

  def toJson: JsValue = {
    import AddressF._
    import Entity._
    import Isdiah._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        NAME -> name,
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
  val id: Option[String],
  val name: String,
  val contactPerson: Option[String] = None,
  val streetAddress: Option[String] = None,
  val city: Option[String] = None,
  val region: Option[String] = None,
  val countryCode: Option[String] = None,
  val email: Option[String] = None,
  val telephone: Option[String] = None,
  val fax: Option[String] = None,
  val url: Option[String] = None
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
      NAME -> optional(text),
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
      DescribedEntity.DESCRIPTIONS -> list(RepositoryDescriptionForm.form.mapping)
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

  val publicationStatus = e.property(Isdiah.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)

  def formable: RepositoryF = new RepositoryF(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.formable)
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
    name = e.stringProperty(NAME),
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

