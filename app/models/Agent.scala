package models

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
import models.base.Field._


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
  
  def addresses: List[AddressRepr] = e.relations(Agent.ADDRESS_REL).map(AddressRepr(_))
  
  def to: AgentDescription = new AgentDescription(
    id = Some(e.id),
    languageCode = languageCode,
    name = e.stringProperty(NAME),
    otherFormsOfName = e.listProperty(OTHER_FORMS_OF_NAME),
    parallelFormsOfName = e.listProperty(PARALLEL_FORMS_OF_NAME),
    addresses = addresses.map(_.to),
    history = e.stringProperty(HISTORY),
    generalContext = e.stringProperty(GENERAL_CONTEXT),
    mandates = e.stringProperty(HISTORY),
    administrativeStructure = e.stringProperty(ADMINISTRATIVE_STRUCTURE),
    records = e.stringProperty(RECORDS),
    buildings = e.stringProperty(BUILDINGS),
    holdings = e.stringProperty(HOLDINGS),
    findingAids = e.stringProperty(FINDING_AIDS)    
  )
}

case class AddressRepr(val e: Entity) extends Formable[Address] {
  def to: Address = new Address(
    id = Some(e.id),
    name = e.stringProperty(Address.ADDRESS_NAME).getOrElse("Unnamed Address"),
    contactPerson = e.stringProperty(Address.CONTACT_PERSON),
    streetAddress = e.stringProperty(Address.STREET_ADDRESS),
    city = e.stringProperty(Address.CITY),
    region = e.stringProperty(Address.REGION),    
    countryCode = e.stringProperty(Address.COUNTRY_CODE),
    email = e.stringProperty(Address.EMAIL),
    telephone = e.stringProperty(Address.TELEPHONE),
    fax = e.stringProperty(Address.FAX),
    url = e.stringProperty(Address.URL)
  )
}

object Agent {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
    
  // Field set
  val NAME = Field("name", "Authorized Form of Name")
  val OTHER_FORMS_OF_NAME = Field("otherFormsOfName", "Other Forms of Name")
  val PARALLEL_FORMS_OF_NAME = Field("parallelFormsOfName", "Parallel forms of Name")

  val HISTORY = Field("history", "History")
  val GENERAL_CONTEXT = Field("generalContext", "General Context")
  val MANDATES = Field("mandates", "Mandates/Sources of Authority")
  val ADMINISTRATIVE_STRUCTURE = Field("administrativeStructure", "Administrative Structure")
  val RECORDS = Field("records", "Records Management and Other Policies")
  val BUILDINGS = Field("buildings", "Building(s)")
  val HOLDINGS = Field("holdings", "Archival and Other Holdings")
  val FINDING_AIDS = Field("findingAids", "Finding Aids")
}

object Address {
  val ADDRESS_NAME = Field("name", "Address Name")
  val CONTACT_PERSON = Field("contactPerson", "Contact Person")
  val STREET_ADDRESS = Field("streetAddress", "Street Address")
  val CITY = Field("city", "City")
  val REGION = Field("region", "Region")
  val COUNTRY_CODE = Field("countryCode", "Country")
  val EMAIL = Field("email", "Email")
  val TELEPHONE = Field("telephone", "Telephone")
  val FAX = Field("fax", "Fax")
  val URL = Field("url", "Web URL")  
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
  val otherFormsOfName: List[String] = Nil,
  val parallelFormsOfName: List[String] = Nil,
  @Annotations.Relation(Agent.ADDRESS_REL) val addresses: List[Address] = Nil,
  val history: Option[String] = None,
  val generalContext: Option[String] = None,
  val mandates: Option[String] = None,
  val administrativeStructure: Option[String] = None,
  val records: Option[String] = None,
  val buildings: Option[String] = None,
  val holdings: Option[String] = None,
  val findingAids: Option[String] = None
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
  val countryCode: Option[String]  = None,
  val email: Option[String] = None,
  val telephone: Option[String] = None,
  val fax: Option[String] = None,
  val url: Option[String] = None
) extends Persistable {
  val isA = EntityType.Address
}




