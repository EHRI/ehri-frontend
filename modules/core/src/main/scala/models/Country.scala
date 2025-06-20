package models

import forms.mappings.optionalText
import models.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._


object CountryF {

  val ABSTRACT = "abstract"
  val HISTORY = "report" // FIXME: Rename to "history"
  val SITUATION = "situation"
  val DATA_SUMMARY = "dataSummary"
  val DATA_EXTENSIVE = "dataExtensive"

  // Defines the sections on the form, with an empty string for the default section
  val FIELDS: Seq[(String, Seq[String])] = Seq("_" -> Seq(ABSTRACT, HISTORY, SITUATION, DATA_SUMMARY, DATA_EXTENSIVE))

  import Entity._

  implicit lazy val _format: Format[CountryF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Country) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ ABSTRACT).formatNullable[String] and
    (__ \ DATA \ HISTORY).formatNullable[String] and
    (__ \ DATA \ SITUATION).formatNullable[String] and
    (__ \ DATA \ DATA_SUMMARY).formatNullable[String] and
    (__ \ DATA \ DATA_EXTENSIVE).formatNullable[String]
  )(CountryF.apply, unlift(CountryF.unapply))

  implicit object Converter extends Writable[CountryF] {
    lazy val _format: Format[CountryF] = CountryF._format
  }
}

case class CountryF(
  isA:EntityType.Value = EntityType.Country,
  id: Option[String],
  identifier: String,
  abs: Option[String],
  history: Option[String],
  situation: Option[String],
  summary: Option[String],
  extensive: Option[String]
) extends ModelData with Persistable {
  def displayText: Option[String] = abs orElse situation
}


object Country {
  import CountryF._
  import Entity._
  import eu.ehri.project.definitions.Ontology._

  implicit lazy val _reads: Reads[Country] = (
    __.read[CountryF](_format) and
    // Latest event
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty(Accessor._reads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Country.apply _)

  implicit object CountryResource extends ContentType[Country]  {
    val entityType = EntityType.Country
    val contentType = ContentTypes.Country
    val _reads: Reads[Country] = Country._reads
  }

  val form: Form[CountryF] = Form(
    mapping(
      ISA -> ignored(EntityType.Country),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> text(minLength=2,maxLength=2).verifying(Constraints.nonEmpty(errorMessage="constraints.mandatory")), // ISO 2-letter field
      ABSTRACT -> optionalText,
      HISTORY -> optionalText,
      SITUATION -> optionalText,
      DATA_SUMMARY -> optionalText,
      DATA_EXTENSIVE -> optionalText
    )(CountryF.apply)(CountryF.unapply)
  )
}


// Stub
case class Country(
  data: CountryF,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends Model
  with Accessible
  with Holder[Repository] {

  type T = CountryF

  override def toStringLang(implicit messages: Messages): String = i18n.countryCodeToName(id)
}
