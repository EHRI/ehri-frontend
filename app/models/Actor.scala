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

object ActorType extends Enumeration {
  type Type = Value
  val Person = Value("person")
  val Family = Value("family")
  val CorporateBody = Value("corporateBody")
}

/**
 * ISAAR Field definitions
 */
case object Isaar {

  val FIELD_PREFIX = "isaar"

  val IDENTIFIER = "identifier"
  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"

  val LANG_CODE = "languageCode"

  // Field set
  val IDENTITY_AREA = "identityArea"
  val AUTHORIZED_FORM_OF_NAME = "name"
  val OTHER_FORMS_OF_NAME = "otherFormsOfName"
  val PARALLEL_FORMS_OF_NAME = "parallelFormsOfName"
  val ENTITY_TYPE = "typeOfEntity"

  // Description area
  val DESCRIPTION_AREA = "descriptionArea"
  val DATES_OF_EXISTENCE = "datesOfExistence"
  val HISTORY = "history"
  val PLACES = "places"
  val LEGAL_STATUS = "legalStatus"
  val FUNCTIONS = "functions"
  val MANDATES = "mandates"
  val INTERNAL_STRUCTURE = "internalStructure"
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
  val SOURCES = "sources"
  val MAINTENANCE_NOTES = "maintenanceNotes"
}


object ActorF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"

  final val UNNAMED_PLACEHOLDER = "UNNAMED Authority"

  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"
}

case class ActorF(
  id: Option[String],
  identifier: String,
  name: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(ActorF.DESC_REL) descriptions: List[ActorDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Actor

  import json.ActorFormat._
  def toJson: JsValue = Json.toJson(this)
}

object ActorDescriptionF {

  case class Details(
    datesOfExistence: Option[String] = None,
    history: Option[String] = None,
    places: Option[String] = None,
    legalStatus: Option[String] = None,
    functions: Option[String] = None,
    mandates: Option[String] = None,
    internalStructure: Option[String] = None,
    generalContext: Option[String] = None
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

case class ActorDescriptionF(
  id: Option[String],
  languageCode: String,
  entityType: ActorType.Value,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  details: ActorDescriptionF.Details,
  control: ActorDescriptionF.Control
) extends Persistable {
  val isA = EntityType.ActorDescription

  import json.IsaarFormat._
  def toJson: JsValue = Json.toJson(this)
}






object ActorDescriptionForm {

  import ActorDescriptionF._
  import Isaar._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      ENTITY_TYPE -> forms.enum(ActorType),
      AUTHORIZED_FORM_OF_NAME -> nonEmptyText,
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      DESCRIPTION_AREA -> mapping(
        DATES_OF_EXISTENCE -> optional(text),
        HISTORY -> optional(text),
        PLACES -> optional(text),
        LEGAL_STATUS -> optional(text),
        FUNCTIONS -> optional(text),
        MANDATES -> optional(text),
        INTERNAL_STRUCTURE -> optional(text),
        GENERAL_CONTEXT -> optional(text)
      )(Details.apply)(Details.unapply),
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
    )(ActorDescriptionF.apply)(ActorDescriptionF.unapply)
  )
}

object ActorForm {

  import ActorF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      DescribedEntity.DESCRIPTIONS -> list(ActorDescriptionForm.form.mapping)
    )(ActorF.apply)(ActorF.unapply)
  )
}


case class Actor(val e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with DescribedEntity
  with Formable[ActorF] {
  override def descriptions: List[ActorDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(ActorDescription(_))

  val publicationStatus = e.property(ActorF.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)

  def formable: ActorF = new ActorF(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.formable)
  )
}

case class ActorDescription(val e: Entity) extends Description with Formable[ActorDescriptionF] {

  import ActorF._
  import ActorDescriptionF._
  import Isaar._

  lazy val item: Option[Actor] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Actor(_))

  def formable: ActorDescriptionF = new ActorDescriptionF(
    id = Some(e.id),
    languageCode = languageCode,
    entityType = e.stringProperty(ENTITY_TYPE).map(ActorType.withName(_)).getOrElse(ActorType.CorporateBody),
    name = e.stringProperty(AUTHORIZED_FORM_OF_NAME).getOrElse(UNNAMED_PLACEHOLDER),
    otherFormsOfName = e.listProperty(OTHER_FORMS_OF_NAME),
    parallelFormsOfName = e.listProperty(PARALLEL_FORMS_OF_NAME),
    details = Details(
      datesOfExistence = e.stringProperty(DATES_OF_EXISTENCE),
      history = e.stringProperty(HISTORY),
      places = e.stringProperty(PLACES),
      legalStatus = e.stringProperty(LEGAL_STATUS),
      functions = e.stringProperty(FUNCTIONS),
      mandates = e.stringProperty(MANDATES),
      internalStructure = e.stringProperty(INTERNAL_STRUCTURE),
      generalContext = e.stringProperty(GENERAL_CONTEXT)
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



