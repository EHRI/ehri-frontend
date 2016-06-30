package models.api.v1

import models._
import models.base.{Accessible, Holder, AnyModel}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
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
    unitDates: Option[Seq[String]],
    biographicalHistory: Option[String],
    archivalHistory: Option[String],
    acquisition: Option[String],
    scopeAndContent: Option[String],
    appraisal: Option[String] = None,
    accruals: Option[String] = None,
    systemOfArrangement: Option[String] = None,
    languageOfMaterials: Option[Seq[String]] = None,
    scriptOfMaterials: Option[Seq[String]] = None
  )

  object DocumentaryUnitDescriptionAttrs {
    implicit val writes = Json.writes[DocumentaryUnitDescriptionAttrs]

    def apply(d: DocumentaryUnitDescriptionF)(implicit messages: Messages): DocumentaryUnitDescriptionAttrs =
      new DocumentaryUnitDescriptionAttrs(
        localId = d.localId,
        languageCode = d.languageCode,
        language = utils.i18n.languageCodeToName(d.languageCode),
        name = d.name,
        parallelFormsOfName = d.identity.parallelFormsOfName.getOrElse(Seq.empty),
        extentAndMedium = d.identity.extentAndMedium,
        unitDates = d.identity.unitDates,
        biographicalHistory = d.context.biographicalHistory,
        archivalHistory = d.context.archivalHistory,
        acquisition = d.context.acquisition,
        scopeAndContent = d.content.scopeAndContent,
        appraisal = d.content.appraisal,
        systemOfArrangement = d.content.systemOfArrangement,
        languageOfMaterials = d.conditions.languageOfMaterials
          .map(_.map(utils.i18n.languageCodeToName)),
        scriptOfMaterials = d.conditions.scriptOfMaterials
          .map(_.map(utils.i18n.scriptCodeToName))
      )
  }

  case class DocumentaryUnitAttrs(
    localId: String,
    alternateIds: Seq[String],
    descriptions: Seq[DocumentaryUnitDescriptionAttrs]
  )

  object DocumentaryUnitAttrs {
    implicit val writes = Json.writes[DocumentaryUnitAttrs]

    def apply(d: DocumentaryUnit)(implicit messages: Messages): DocumentaryUnitAttrs =
      new DocumentaryUnitAttrs(
        localId = d.model.identifier,
        alternateIds = d.model.otherIdentifiers.getOrElse(Seq.empty),
        descriptions = d.model.orderedDescriptions.map(DocumentaryUnitDescriptionAttrs.apply)
      )
  }

  case class AddressAttrs(
    name: Option[String],
    contactPerson: Option[String] = None,
    streetAddress: Option[String] = None,
    city: Option[String] = None,
    region: Option[String] = None,
    postalCode: Option[String] = None,
    countryCode: Option[String] = None,
    email: Seq[String] = Nil,
    telephone: Seq[String] = Nil,
    fax: Seq[String] = Nil,
    url: Seq[String] = Nil
  )

  object AddressAttrs {
    implicit val writes = Json.writes[AddressAttrs]

    def apply(a: AddressF) = new AddressAttrs(
      a.name,
      a.contactPerson,
      a.streetAddress,
      a.city,
      a.region,
      a.postalCode,
      a.countryCode,
      a.email,
      a.telephone,
      a.fax,
      a.url
    )
  }

  case class RepositoryAttrs(
    name: Option[String] = None,
    parallelFormsOfName: Option[Seq[String]] = None,
    otherFormsOfName: Option[Seq[String]] = None,
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
    publicAreas: Option[String] = None
  )

  object RepositoryAttrs {
    implicit val writes = Json.writes[RepositoryAttrs]

    def apply(r: Repository)(implicit messages: Messages): RepositoryAttrs = {
      r.model.primaryDescription.map { d =>
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
          publicAreas = d.services.publicAreas
        )
      }.getOrElse {
        RepositoryAttrs()
      }
    }
  }

  case class DocumentaryUnitLinks(
    self: String,
    search: String,
    holder: Option[String],
    parent: Option[String]
  )

  object DocumentaryUnitLinks {
    implicit val writes = Json.writes[DocumentaryUnitLinks]
  }

  case class DocumentaryUnitRelations(
    holder: Option[JsValue],
    parent: Option[JsValue]
  )

  object DocumentaryUnitRelations {
    // Not using default writes here because missing (Optional)
    // relations should be expressed using null
    // http://jsonapi.org/format/#document-resource-object-related-resource-links
    implicit val writes = new Writes[DocumentaryUnitRelations] {
      def writes(r: DocumentaryUnitRelations): JsValue = Json.obj(
        "holder" -> r.holder,
        "parent" -> r.parent
      )
    }
  }

  case class HistoricalAgentAttrs(
    name: Option[String] = None,
    otherFormsOfName: Option[Seq[String]] = None,
    datesOfExistence: Option[String] = None,
    history: Option[String] = None,
    places: Option[Seq[String]] = None,
    legalStatus: Option[Seq[String]] = None,
    functions: Option[Seq[String]] = None,
    mandates: Option[Seq[String]] = None,
    internalStructure: Option[String] = None,
    generalContext: Option[String] = None
  )

  object HistoricalAgentAttrs {
    implicit val writes = Json.writes[HistoricalAgentAttrs]

    def apply(a: HistoricalAgent)(implicit messages: Messages): HistoricalAgentAttrs = {
      a.model.primaryDescription.map { d =>
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

  case class RepositoryLinks(
    self: String,
    search: String,
    country: Option[String]
  )

  object RepositoryLinks {
    implicit val writes = Json.writes[RepositoryLinks]
  }

  case class RepositoryRelations(
    country: Option[JsValue]
  )

  object RepositoryRelations {
    implicit val writes = Json.writes[RepositoryRelations]
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
    implicit val writes = Json.writes[CountryAttrs]

    def apply(c: Country)(implicit requestHeader: RequestHeader, messages: Messages): CountryAttrs =
      new CountryAttrs(
        identifier = c.model.identifier,
        name = c.toStringLang,
        `abstract` = c.model.abs,
        history = c.model.history,
        situation = c.model.situation,
        summary = c.model.summary,
        extensive = c.model.extensive
      )
  }

  case class CountryLinks(
    self: String,
    search: String
  )

  object CountryLinks {
    implicit val writes = Json.writes[CountryLinks]
  }

  case class JsonApiResponse(
    data: AnyModel,
    links: Option[JsValue] = None,
    included: Option[Seq[AnyModel]] = None
  )

  object JsonApiResponse {
    implicit def writes(implicit amw: Writes[AnyModel]): Writes[JsonApiResponse] = Json.writes[JsonApiResponse]
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
    implicit val writes = Json.writes[JsonApiResponseData]
  }

  case class ResourceIdentifier(
    id: String,
    `type`: String
  )

  object ResourceIdentifier {
    implicit val writes = Json.writes[ResourceIdentifier]

    def apply(m: AnyModel) = new ResourceIdentifier(m.id, m.isA.toString)
  }

  case class PaginationLinks(
    first: String,
    last: String,
    prev: Option[String] = None,
    next: Option[String] = None
  )

  object PaginationLinks {
    implicit val writes = Json.writes[PaginationLinks]
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
    implicit val writes = Json.writes[JsonApiError]
  }

  case class JsonApiListResponse(
    data: Seq[AnyModel],
    links: PaginationLinks,
    included: Option[Seq[AnyModel]]
  )

  object JsonApiListResponse {
    implicit def writes(implicit amw: Writes[AnyModel]): Writes[JsonApiListResponse] =
      Json.writes[JsonApiListResponse]
  }
}
