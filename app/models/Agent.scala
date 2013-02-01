package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */
import defines.PublicationStatus
import defines.enum
import base._

import models.forms.Isdiah._
import models.forms.{AgentF,AgentDescriptionF,AddressF}
import scala.Some


case class Agent(val e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with DescribedEntity
  with Formable[AgentF] {
  override def descriptions: List[AgentDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(AgentDescription(_))

  val publicationStatus = e.property("publicationStatus").flatMap(enum(PublicationStatus).reads(_).asOpt)


  def to: AgentF = new AgentF(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.to)
  )
}

case class AgentDescription(val e: Entity) extends Description with Formable[AgentDescriptionF] {

  import AgentDescriptionF._

  def addresses: List[Address] = e.relations(AgentF.ADDRESS_REL).map(Address(_))

  def to: AgentDescriptionF = new AgentDescriptionF(
    id = Some(e.id),
    languageCode = languageCode,
    name = e.stringProperty(NAME),
    otherFormsOfName = e.listProperty(OTHER_FORMS_OF_NAME),
    parallelFormsOfName = e.listProperty(PARALLEL_FORMS_OF_NAME),
    addresses = addresses.map(_.to),
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
  def to: AddressF = new AddressF(
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

