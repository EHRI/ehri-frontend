package models

import models.Description.LANG_CODE

/**
  * ISDIAH Field definitions
  */
case object Isdiah {

  object RepositoryType extends Enumeration {
    type Type = Value
  }

  val IDENTIFIER = "identifier"
  val ADMINISTRATION_AREA = "administrationArea"

  // Field set
  val IDENTITY_AREA = "identityArea"
  val AUTHORIZED_FORM_OF_NAME = "name"
  val OTHER_FORMS_OF_NAME = "otherFormsOfName"
  val PARALLEL_FORMS_OF_NAME = "parallelFormsOfName"
  val INSTITUTION_TYPE = "institutionType"

  // AddressF
  val ADDRESS_AREA = "addressArea"
  val ADDRESS_NAME = "addressName"
  val CONTACT_PERSON = "contactPerson"
  val STREET_ADDRESS = "street"
  val CITY = "municipality"
  val REGION = "firstdem"
  val COUNTRY_CODE = "countryCode"
  val POSTAL_CODE = "postalCode"
  val EMAIL = "email"
  val TELEPHONE = "telephone"
  val FAX = "fax"
  val URL = "webpage"

  val DESCRIPTION_AREA = "descriptionArea"
  val HISTORY = "history"
  val GEOCULTURAL_CONTEXT = "geoculturalContext"
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

  val ACCESS_POINTS = "accessPoints"

  val FIELDS: Seq[(String, Seq[String])] = List(
    "_" -> Seq(
      IDENTIFIER,
      LANG_CODE,
    ),
    IDENTITY_AREA -> Seq(
      AUTHORIZED_FORM_OF_NAME,
      OTHER_FORMS_OF_NAME,
      PARALLEL_FORMS_OF_NAME,
      INSTITUTION_TYPE
    ),
    ADDRESS_AREA -> List(
      ADDRESS_NAME,
      CONTACT_PERSON,
      STREET_ADDRESS,
      CITY,
      REGION,
      COUNTRY_CODE,
      POSTAL_CODE,
      EMAIL,
      TELEPHONE,
      FAX,
      URL
    ),
    DESCRIPTION_AREA -> List(
      HISTORY,
      GEOCULTURAL_CONTEXT,
      MANDATES,
      ADMINISTRATIVE_STRUCTURE,
      RECORDS,
      BUILDINGS,
      HOLDINGS,
      FINDING_AIDS,
    ),
    ACCESS_AREA -> List(
      OPENING_TIMES,
      CONDITIONS,
      ACCESSIBILITY,
    ),
    SERVICES_AREA -> List(
      RESEARCH_SERVICES,
      REPROD_SERVICES,
      PUBLIC_AREAS,
    ),
    CONTROL_AREA -> List(
      DESCRIPTION_IDENTIFIER,
      INSTITUTION_IDENTIFIER,
      RULES_CONVENTIONS,
      STATUS,
      LEVEL_OF_DETAIL,
      DATES_CVD,
      LANGUAGES_USED,
      SCRIPTS_USED,
      SOURCES,
      MAINTENANCE_NOTES
    )
  )
}

