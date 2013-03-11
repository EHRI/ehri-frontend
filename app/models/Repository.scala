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




object RepositoryF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"

  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"
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

  import json.IsdiahFormat._
  def toJson: JsValue = Json.toJson(this)
}

object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"
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
) {
  val isA = EntityType.Address
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
  import RepositoryF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      DescribedEntity.DESCRIPTIONS -> list(RepositoryDescriptionForm.form.mapping),
      PRIORITY -> optional(number(min = -1, max = 5))
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
  val publicationStatus = e.property(RepositoryF.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)
  val priority = e.property(RepositoryF.PRIORITY).flatMap(_.asOpt[Int])

  import json.RepositoryFormat._
  def formable: RepositoryF = Json.toJson(e).as[RepositoryF]
}

case class RepositoryDescription(val e: Entity) extends Description with Formable[RepositoryDescriptionF] {

  import RepositoryDescriptionF._
  import Isdiah._

  lazy val item: Option[Repository] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Repository(_))
  def addresses: List[Address] = e.relations(RepositoryF.ADDRESS_REL).map(Address(_))

  import json.IsdiahFormat._
  def formable: RepositoryDescriptionF = Json.toJson(e).as[RepositoryDescriptionF]
}

case class Address(val e: Entity) extends AccessibleEntity with Formable[AddressF] {
  import json.AddressFormat._
  def formable: AddressF = Json.toJson(e).as[AddressF]
}

