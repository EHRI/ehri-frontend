package models.forms

import play.api.data._
import play.api.data.Forms._
import models._
import base.{AttributeSet, Persistable}

import defines._

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

  // AddressF
  val ADDRESS_AREA = "addressArea"
  val ADDRESS_NAME = "name"
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



object AgentF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"
}

case class AgentF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(AgentF.DESC_REL) val descriptions: List[AgentDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Agent
}

case class AgentDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val name: Option[String] = None,
  val otherFormsOfName: Option[List[String]] = None,
  val parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(AgentF.ADDRESS_REL) val addresses: List[AddressF] = Nil,
  val details: AgentDescriptionF.Details,
  val access: AgentDescriptionF.Access,
  val services: AgentDescriptionF.Services,
  val control: AgentDescriptionF.Control
) extends Persistable {
  val isA = EntityType.AgentDescription
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
}

object AgentDescriptionF {

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



object AgentDescriptionForm {
  import AgentDescriptionF._
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      "languageCode" -> nonEmptyText,
      NAME -> optional(text),
      OTHER_FORMS_OF_NAME -> optional(list(text)),
      PARALLEL_FORMS_OF_NAME -> optional(list(text)),
      "addresses" -> list(
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
        LANGUAGES_USED -> optional(list(text)),
        SCRIPTS_USED -> optional(list(text)),
        SOURCES -> optional(text),
        MAINTENANCE_NOTES -> optional(text)
      )(Control.apply)(Control.unapply)
    )(AgentDescriptionF.apply)(AgentDescriptionF.unapply)
  )
}

object AgentForm {

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      Isdiah.NAME -> nonEmptyText,
      Isdiah.PUBLICATION_STATUS -> optional(enum(defines.PublicationStatus)),
      "descriptions" -> list(AgentDescriptionForm.form.mapping)
    )(AgentF.apply)(AgentF.unapply)
  )
}
