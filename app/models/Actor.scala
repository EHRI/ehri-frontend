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
}

case class ActorF(
  id: Option[String],
  identifier: String,
  name: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(ActorF.DESC_REL) descriptions: List[ActorDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Actor

  def toJson: JsValue = {
    import ActorF._
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
  name: Option[String] = None,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  details: ActorDescriptionF.Details,
  control: ActorDescriptionF.Control
) extends Persistable {
  val isA = EntityType.ActorDescription

  def toJson: JsValue = {
    import AddressF._
    import Entity._
    import Isaar._

    Json.obj(
      ID -> id,
      TYPE -> isA,
      DATA -> Json.obj(
        NAME -> name,
        ENTITY_TYPE -> entityType,
        LANG_CODE -> languageCode,
        OTHER_FORMS_OF_NAME -> otherFormsOfName,
        PARALLEL_FORMS_OF_NAME -> parallelFormsOfName,
        DATES_OF_EXISTENCE -> details.datesOfExistence,
        HISTORY -> details.history,
        PLACES -> details.places,
        LEGAL_STATUS -> details.legalStatus,
        FUNCTIONS -> details.functions,
        MANDATES -> details.mandates,
        INTERNAL_STRUCTURE -> details.internalStructure,
        GENERAL_CONTEXT -> details.generalContext,
        DESCRIPTION_IDENTIFIER -> control.descriptionIdentifier,
        INSTITUTION_IDENTIFIER -> control.institutionIdentifier,
        RULES_CONVENTIONS -> control.rulesAndConventions,
        STATUS -> control.status,
        LEVEL_OF_DETAIL -> control.levelOfDetail,
        DATES_CVD -> control.datesCDR,
        LANGUAGES_USED -> control.languages,
        SCRIPTS_USED -> control.scripts,
        SOURCES -> control.sources,
        MAINTENANCE_NOTES -> control.maintenanceNotes
      )
    )
  }
}






object ActorDescriptionForm {

  import ActorDescriptionF._
  import Isaar._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      ENTITY_TYPE -> forms.enum(ActorType),
      NAME -> optional(text),
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

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      Isdiah.NAME -> nonEmptyText,
      Isdiah.PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
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

  val publicationStatus = e.property(Isdiah.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)

  def formable: ActorF = new ActorF(
    id = Some(e.id),
    identifier = identifier,
    name = name,
    publicationStatus = publicationStatus,
    descriptions = descriptions.map(_.formable)
  )
}

case class ActorDescription(val e: Entity) extends Description with Formable[ActorDescriptionF] {

  import ActorDescriptionF._
  import Isaar._

  lazy val item: Option[Actor] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Actor(_))

  def formable: ActorDescriptionF = new ActorDescriptionF(
    id = Some(e.id),
    languageCode = languageCode,
    entityType = e.stringProperty(ENTITY_TYPE).map(ActorType.withName(_)).getOrElse(ActorType.CorporateBody),
    name = e.stringProperty(NAME),
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



