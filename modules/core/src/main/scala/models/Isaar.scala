package models

import models.Description.LANG_CODE
import play.api.libs.json.Format
import utils.EnumUtils

/**
  * ISAAR Field definitions
  */
case object Isaar {

  object HistoricalAgentType extends Enumeration {
    type Type = Value
    val Person = Value("person")
    val Family = Value("family")
    val CorporateBody = Value("corporateBody")

    implicit val format: Format[Isaar.HistoricalAgentType.Value] = EnumUtils.enumFormat(this)
  }

  val IDENTIFIER = "identifier"
  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"

  val ADMINISTRATION_AREA = "administrationArea"

  // Field set
  val IDENTITY_AREA = "identityArea"
  val AUTHORIZED_FORM_OF_NAME = "name"
  val OTHER_FORMS_OF_NAME = "otherFormsOfName"
  val PARALLEL_FORMS_OF_NAME = "parallelFormsOfName"
  val ENTITY_TYPE = "typeOfEntity"

  // Description area
  val DESCRIPTION_AREA = "descriptionArea"
  val DATES = "dates"
  val DATES_OF_EXISTENCE = "datesOfExistence"
  val HISTORY = "biographicalHistory"
  val PLACES = "place"
  val LEGAL_STATUS = "legalStatus"
  val FUNCTIONS = "functions"
  val MANDATES = "mandates"
  val INTERNAL_STRUCTURE = "structure"
  val GENERAL_CONTEXT = "generalContext"

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
  val SOURCES = "source"
  val MAINTENANCE_NOTES = "maintenanceNotes"

  val ACCESS_POINTS = "accessPoints"

  val FIELDS: Seq[(String, Seq[String])] = List(
    "_" -> Seq(
      IDENTIFIER,
      LANG_CODE,
    ),
    IDENTITY_AREA -> List(
      AUTHORIZED_FORM_OF_NAME,
      ENTITY_TYPE,
      OTHER_FORMS_OF_NAME,
      PARALLEL_FORMS_OF_NAME
    ),
    DESCRIPTION_AREA -> List(
      DATES,
      DATES_OF_EXISTENCE,
      HISTORY,
      PLACES,
      LEGAL_STATUS,
      FUNCTIONS,
      MANDATES,
      INTERNAL_STRUCTURE,
      GENERAL_CONTEXT
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

