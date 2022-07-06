package models.api.v1

import models._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.RequestHeader

object JsonApiV1 {

  final val JSONAPI_MIMETYPE = "application/vnd.api+json"

  def holderMeta[T <: Holder[_] with Accessible](t: T): JsObject = Json.obj(
    "subitems" -> t.childCount
  )

  def meta[T <: Accessible](t: T): JsObject = Json.obj(
    "updated" -> t.latestEvent.map(_.time)
  )

  case class DocumentaryUnitDescriptionAttrs(
    localId: Option[String],
    languageCode: String,
    language: String,
    name: String,
    parallelFormsOfName: Seq[String],
    extentAndMedium: Option[String],
    unitDates: Seq[String],
    biographicalHistory: Option[String],
    archivalHistory: Option[String],
    acquisition: Option[String],
    scopeAndContent: Option[String],
    appraisal: Option[String] = None,
    accruals: Option[String] = None,
    systemOfArrangement: Option[String] = None,
    languageOfMaterials: Seq[String] = Nil,
    scriptOfMaterials: Seq[String] = Nil
  )

  object DocumentaryUnitDescriptionAttrs {
    implicit val writes: Writes[DocumentaryUnitDescriptionAttrs] = Json.writes[DocumentaryUnitDescriptionAttrs]

    def apply(d: DocumentaryUnitDescriptionF)(implicit messages: Messages): DocumentaryUnitDescriptionAttrs =
      new DocumentaryUnitDescriptionAttrs(
        localId = d.localId,
        languageCode = d.languageCode,
        language = i18n.languageCodeToName(d.languageCode),
        name = d.name,
        parallelFormsOfName = d.identity.parallelFormsOfName,
        extentAndMedium = d.identity.extentAndMedium,
        unitDates = d.identity.unitDates,
        biographicalHistory = d.context.biographicalHistory,
        archivalHistory = d.context.archivalHistory,
        acquisition = d.context.acquisition,
        scopeAndContent = d.content.scopeAndContent,
        appraisal = d.content.appraisal,
        systemOfArrangement = d.content.systemOfArrangement,
        languageOfMaterials = d.conditions.languageOfMaterials.map(i18n.languageCodeToName),
        scriptOfMaterials = d.conditions.scriptOfMaterials.map(i18n.scriptCodeToName)
      )
  }

  case class DocumentaryUnitAttrs(
    localId: String,
    alternateIds: Seq[String],
    descriptions: Seq[DocumentaryUnitDescriptionAttrs]
  )

  object DocumentaryUnitAttrs {
    implicit val writes: Writes[DocumentaryUnitAttrs] = Json.writes[DocumentaryUnitAttrs]

    def apply(d: DocumentaryUnit)(implicit messages: Messages): DocumentaryUnitAttrs =
      new DocumentaryUnitAttrs(
        localId = d.data.identifier,
        alternateIds = d.data.otherIdentifiers.getOrElse(Seq.empty),
        descriptions = d.data.currentLangFirstDescriptions.map(DocumentaryUnitDescriptionAttrs.apply)
      )
  }

  case class AddressAttrs(
    name: Option[String],
    contactPerson: Option[String] = None,
    streetAddress: Option[String] = None,
    city: Option[String] = None,
    region: Option[String] = None,
    postalCode: Option[String] = None,
    country: Option[String] = None,
    countryCode: Option[String] = None,
    email: Seq[String] = Nil,
    telephone: Seq[String] = Nil,
    fax: Seq[String] = Nil,
    url: Seq[String] = Nil
  )

  object AddressAttrs {
    implicit val writes: Writes[AddressAttrs] = Json.writes[AddressAttrs]

    def apply(a: AddressF)(implicit messages: Messages): AddressAttrs = new AddressAttrs(
      a.name,
      a.contactPerson,
      a.streetAddress,
      a.city,
      a.region,
      a.postalCode,
      a.countryCode.map(i18n.countryCodeToName),
      a.countryCode,
      a.email,
      a.telephone,
      a.fax,
      a.url
    )
  }

  case class RepositoryAttrs(
    name: Option[String] = None,
    parallelFormsOfName: Seq[String] = Nil,
    otherFormsOfName: Seq[String] = Nil,
    address: Option[AddressAttrs] = None,
    history: Option[String] = None,
    generalContext: Option[String] = None,
    mandates: Option[String] = None,
    administrativeStructure: Option[String] = None,
    records: Option[String] = None,
    buildings: Option[String] = None,
    holdings: Option[String] = None,
    findingAids: Option[String] = None,
    openingTimes: Option[String] = None,
    conditions: Option[String] = None,
    accessibility: Option[String] = None,
    researchServices: Option[String] = None,
    reproductionServices: Option[String] = None,
    publicAreas: Option[String] = None,
    geo: Option[GeoPoint] = None
  )

  object RepositoryAttrs {
    implicit val writes: Writes[RepositoryAttrs] = Json.writes[RepositoryAttrs]

    def apply(r: Repository)(implicit messages: Messages): RepositoryAttrs = {
      r.data.primaryDescription.map { d =>
        new RepositoryAttrs(
          name = Some(d.name),
          parallelFormsOfName = d.parallelFormsOfName,
          otherFormsOfName = d.otherFormsOfName,
          address = d.addresses.headOption.map(a => AddressAttrs(a)),
          history = d.details.history,
          generalContext = d.details.generalContext,
          mandates = d.details.mandates,
          administrativeStructure = d.details.administrativeStructure,
          records = d.details.records,
          buildings = d.details.buildings,
          holdings = d.details.holdings,
          openingTimes = d.access.openingTimes,
          conditions = d.access.conditions,
          accessibility = d.access.accessibility,
          researchServices = d.services.researchServices,
          reproductionServices = d.services.reproductionServices,
          publicAreas = d.services.publicAreas,
          geo = for(lat <- r.data.latitude; lon <- r.data.longitude)
            yield GeoPoint(lat, lon)
        )
      }.getOrElse {
        RepositoryAttrs()
      }
    }
  }

  case class DocumentaryUnitLinks(
    self: String,
    search: String,
    holder: Option[String] = None,
    parent: Option[String] = None
  )

  object DocumentaryUnitLinks {
    implicit val writes: Writes[DocumentaryUnitLinks] = Json.writes[DocumentaryUnitLinks]
  }

  case class DocumentaryUnitRelations(
    holder: Option[JsValue] = None,
    parent: Option[JsValue] = None
  )

  object DocumentaryUnitRelations {
    // Not using default writes here because missing (Optional)
    // relations should be expressed using null
    // http://jsonapi.org/format/#document-resource-object-related-resource-links
    implicit val writes: Writes[DocumentaryUnitRelations] = Writes[DocumentaryUnitRelations] { r =>
      Json.obj(
        "holder" -> r.holder,
        "parent" -> r.parent
      )
    }
  }

  case class HistoricalAgentAttrs(
    name: Option[String] = None,
    otherFormsOfName: Seq[String] = Nil,
    datesOfExistence: Option[String] = None,
    history: Option[String] = None,
    places: Seq[String] = Nil,
    legalStatus: Seq[String] = Nil,
    functions: Seq[String] = Nil,
    mandates: Seq[String] = Nil,
    internalStructure: Option[String] = None,
    generalContext: Option[String] = None
  )

  object HistoricalAgentAttrs {
    implicit val writes: Writes[HistoricalAgentAttrs] = Json.writes[HistoricalAgentAttrs]

    def apply(a: HistoricalAgent)(implicit messages: Messages): HistoricalAgentAttrs = {
      a.data.primaryDescription.map { d =>
        new HistoricalAgentAttrs(
          name = Some(d.name),
          otherFormsOfName = d.otherFormsOfName,
          datesOfExistence = d.details.datesOfExistence,
          history = d.details.history,
          places = d.details.places,
          legalStatus = d.details.legalStatus,
          functions = d.details.functions,
          mandates = d.details.mandates,
          internalStructure = d.details.internalStructure,
          generalContext = d.details.generalContext
        )
      }.getOrElse {
        HistoricalAgentAttrs()
      }
    }
  }

  case class HistoricalAgentLinks(
    self: String,
    related: String
  )

  object HistoricalAgentLinks {
    implicit val writes: Writes[HistoricalAgentLinks] = Json.writes[HistoricalAgentLinks]
  }

  case class RepositoryLinks(
    self: String,
    search: String,
    country: Option[String]
  )

  object RepositoryLinks {
    implicit val writes: Writes[RepositoryLinks] = Json.writes[RepositoryLinks]
  }

  case class RepositoryRelations(
    country: Option[JsValue]
  )

  object RepositoryRelations {
    implicit val writes: Writes[RepositoryRelations] = Json.writes[RepositoryRelations]
  }

  case class CountryAttrs(
    identifier: String,
    name: String,
    `abstract`: Option[String],
    history: Option[String],
    situation: Option[String],
    summary: Option[String],
    extensive: Option[String]
  )

  object CountryAttrs {
    implicit val writes: Writes[CountryAttrs] = Json.writes[CountryAttrs]

    def apply(c: Country)(implicit requestHeader: RequestHeader, messages: Messages): CountryAttrs =
      new CountryAttrs(
        identifier = c.data.identifier,
        name = c.toStringLang,
        `abstract` = c.data.abs,
        history = c.data.history,
        situation = c.data.situation,
        summary = c.data.summary,
        extensive = c.data.extensive
      )
  }

  case class CountryLinks(
    self: String,
    search: String
  )

  object CountryLinks {
    implicit val writes: Writes[CountryLinks] = Json.writes[CountryLinks]
  }

  case class ConceptDescriptionAttrs(
    languageCode: String,
    languageTag: String,
    language: String,
    prefLabel: String,
    definition: Option[Seq[String]] = None,
    altLabels: Option[Seq[String]] = None,
    note: Option[Seq[String]] = None,
    scopeNote: Option[Seq[String]] = None,
    editorialNote: Option[Seq[String]] = None,
  )

  object ConceptDescriptionAttrs {
    implicit val writes: Writes[ConceptDescriptionAttrs] = Json.writes[ConceptDescriptionAttrs]

    def apply(d: ConceptDescriptionF)(implicit messages: Messages): ConceptDescriptionAttrs = new ConceptDescriptionAttrs(
      languageCode = d.languageCode,
      languageTag = d.languageCode2,
      language = i18n.languageCodeToName(d.languageCode),
      prefLabel = d.name,
      altLabels = if (d.altLabels.isEmpty) None else Some(d.altLabels),
      definition = if(d.definition.isEmpty) None else Some(d.definition),
      note = if (d.note.isEmpty) None else Some(d.note),
      scopeNote = if (d.scopeNote.isEmpty) None else Some(d.scopeNote),
      editorialNote = if (d.editorialNote.isEmpty) None else Some(d.editorialNote)
    )
  }

  case class ConceptAttrs(
    localId: String,
    descriptions: Seq[ConceptDescriptionAttrs],
    seeAlso: Option[Seq[String]] = None,
    uri: Option[String] = None,
    geo: Option[GeoPoint] = None
  )

  object ConceptAttrs {
    implicit val writes: Writes[ConceptAttrs] = Json.writes[ConceptAttrs]

    def apply(c: Concept)(implicit requestHeader: RequestHeader, messages: Messages): ConceptAttrs =
      new ConceptAttrs(
        c.data.identifier,
        descriptions = c.data.currentLangFirstDescriptions.map(d => ConceptDescriptionAttrs(d)),
        seeAlso = if(c.data.seeAlso.isEmpty) None else Some(c.data.seeAlso),
        uri = c.data.uri,
        geo = for(lat <- c.data.latitude; lon <- c.data.longitude)
          yield GeoPoint(lat, lon)
      )
  }

  case class ConceptLinks(
    self: String,
    search: String,
    related: String,
    parent: Option[String] = None,
  )

  object ConceptLinks {
    implicit val writes: Writes[ConceptLinks] = Json.writes[ConceptLinks]
  }

  case class JsonApiResponse(
    data: Model,
    links: Option[JsValue] = None,
    included: Option[Seq[Model]] = None
  )

  object JsonApiResponse {
    implicit def writes(implicit amw: Writes[Model]): Writes[JsonApiResponse] = Json.writes[JsonApiResponse]
  }

  case class JsonApiResponseData(
    id: String,
    `type`: String,
    attributes: JsValue,
    relationships: Option[JsValue] = None,
    links: Option[JsValue] = None,
    meta: Option[JsValue] = None
  )

  object JsonApiResponseData {
    implicit def writes(implicit requestHeader: RequestHeader): Writes[JsonApiResponseData] =
      Json.writes[JsonApiResponseData]
  }

  case class ResourceIdentifier(
    id: String,
    `type`: String
  )

  object ResourceIdentifier {
    implicit val writes: Writes[ResourceIdentifier] = Json.writes[ResourceIdentifier]

    def apply(m: Model) = new ResourceIdentifier(m.id, m.isA.toString)
  }

  case class PaginationLinks(
    first: String,
    last: String,
    prev: Option[String] = None,
    next: Option[String] = None
  )

  object PaginationLinks {
    implicit val writes: Writes[PaginationLinks] = Json.writes[PaginationLinks]
  }

  case class JsonApiError(
    status: String,
    title: String,
    detail: Option[String] = None,
    code: Option[String] = None,
    id: Option[String] = None,
    meta: Option[JsValue] = None
  )

  object JsonApiError {
    implicit val writes: Writes[JsonApiError] = Json.writes[JsonApiError]
  }

  case class GeoPoint(
    latitude: BigDecimal,
    longitude: BigDecimal
  )

  object GeoPoint {
    implicit val writes: Writes[GeoPoint] = Writes { p =>
      Json.obj(
        "type" -> "Point",
        "coordinates" -> Json.arr(p.latitude, p.longitude)
      )
    }
  }

  case class JsonApiListResponse(
    data: Seq[Model],
    links: PaginationLinks,
    included: Option[Seq[Model]] = None,
    meta: Option[JsValue] = None
  )

  object JsonApiListResponse {
    implicit def writes(implicit amw: Writes[Model]): Writes[JsonApiListResponse] =
      Json.writes[JsonApiListResponse]
  }
}
