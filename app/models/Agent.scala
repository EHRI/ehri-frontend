package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */
import defines.EntityType
import defines.PublicationStatus
import defines.enum
import models.base.AccessibleEntity
import models.base.DescribedEntity
import models.base.Description
import models.base.Formable
import models.base.NamedEntity
import models.base.Persistable
import models.base.Field
import models.base.AttributeSet

/**
 * ISDIAH Field definitions
 */
case object Isdiah {
  val IDENTIFIER = "identifier"
  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"

  // Field set
  val IDENTITY_AREA = "identityArea"
  val AUTHORIZED_FORM_OF_NAME = "authorizedFormOfName"
  val OTHER_FORMS_OF_NAME = "otherFormsOfName"
  val PARALLEL_FORMS_OF_NAME = "parallelFormsOfName"
  val INSTITUTION_TYPE = "institutionType"

  // Address
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



case class AgentRepr(val e: Entity) extends NamedEntity with AccessibleEntity with DescribedEntity with Formable[Agent] {
  override def descriptions: List[AgentDescriptionRepr] = e.relations(DescribedEntity.DESCRIBES_REL).map(AgentDescriptionRepr(_))

  val publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt)


  def to: Agent = new Agent(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.to)
  )
}

case class AgentDescriptionRepr(val e: Entity) extends Description with Formable[AgentDescription] {

  import Agent._
  import AgentDescription._
  import Isdiah._

  def addresses: List[AddressRepr] = e.relations(Agent.ADDRESS_REL).map(AddressRepr(_))

  def to: AgentDescription = new AgentDescription(
    id = Some(e.id),
    languageCode = languageCode,
    name = e.stringProperty(NAME),
    otherFormsOfName = e.listProperty(OTHER_FORMS_OF_NAME),
    parallelFormsOfName = e.listProperty(PARALLEL_FORMS_OF_NAME),
    addresses = addresses.map(_.to),
    details = Details(
      history = e.stringProperty(HISTORY),
      generalContext = e.stringProperty(GENERAL_CONTEXT),
      mandates = e.stringProperty(HISTORY),
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

case class AddressRepr(val e: Entity) extends Formable[Address] {
  import Isdiah._
  def to: Address = new Address(
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

object Agent {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
}

case class Agent(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(Agent.DESC_REL) val descriptions: List[AgentDescription] = Nil
) extends Persistable {
  val isA = EntityType.Agent
}

case class AgentDescription(
  val id: Option[String],
  val languageCode: String,
  val name: Option[String] = None,
  val otherFormsOfName: Option[List[String]] = None,
  val parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(Agent.ADDRESS_REL) val addresses: List[Address] = Nil,
  val details: AgentDescription.Details,
  val access: AgentDescription.Access,
  val services: AgentDescription.Services,
  val control: AgentDescription.Control
) extends Persistable {
  val isA = EntityType.AgentDescription
}

case class Address(
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
}

object AgentDescription {

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

