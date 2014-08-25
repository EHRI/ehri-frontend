package models

import base._

import models.base.Persistable
import defines.{ContentTypes, EntityType}
import models.json._
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import backend.{BackendReadable, BackendContentType, BackendResource, BackendWriteable}


object CountryF {

  val REPORT = "report"
  val ABSTRACT = "abstract"

  import Entity._

  implicit val countryWrites: Writes[CountryF] = new Writes[CountryF] {
    def writes(d: CountryF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          ABSTRACT -> d.abs,
          REPORT -> d.report
        )
      )
    }
  }

  lazy implicit val countryReads: Reads[CountryF] = (
    (__ \ TYPE).readIfEquals(EntityType.Country) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ DATA \ ABSTRACT).readNullable[String] and
    (__ \ DATA \ REPORT).readNullable[String]
  )(CountryF.apply _)

  implicit object Converter extends BackendWriteable[CountryF] {
    lazy val restFormat = Format(countryReads,countryWrites)
  }
}

case class CountryF(
  isA:EntityType.Value = EntityType.Country,
  id: Option[String],
  identifier: String,
  abs: Option[String],
  report: Option[String]
) extends Model with Persistable


object Country {
  import eu.ehri.project.definitions.Ontology._
  import Entity._
  import CountryF._

  implicit val metaReads: Reads[Country] = (
    __.read[CountryF](countryReads) and
    // Latest event
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).nullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Country.apply _)

  implicit object Converter extends BackendReadable[Country] {
    val restReads = metaReads
  }

  implicit object Resource extends BackendResource[Country] with BackendContentType[Country] {
    val entityType = EntityType.Country
    val contentType = ContentTypes.Country
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Country),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=2,maxLength=2), // ISO 2-letter field
      ABSTRACT -> optional(nonEmptyText),
      REPORT -> optional(nonEmptyText)
    )(CountryF.apply)(CountryF.unapply)
  )
}


// Stub
case class Country(
  model: CountryF,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[CountryF]
  with Accessible
  with Holder[Repository] {

  override def toStringLang(implicit lang: Lang) = views.Helpers.countryCodeToName(id)
}