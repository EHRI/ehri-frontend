package client

import models.json._
import models._
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.EnumUtils

package object json {

  implicit object datePeriodJson extends ClientWriteable[DatePeriodF] {
    lazy val _clientFormat: Format[DatePeriodF] = Json.format[DatePeriodF]
  }

  implicit object addressJson extends ClientWriteable[AddressF] {
    lazy val _clientFormat: Writes[AddressF] = Json.writes[AddressF]
  }

  implicit object anyModelJson extends ClientWriteable[Model] {
    private val logger = Logger(getClass)
    private val _clientFormatRegistry: PartialFunction[EntityType.Value, Format[Model]] = {
      case EntityType.Repository => repositoryJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Country => countryJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.DocumentaryUnit => documentaryUnitJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Vocabulary => vocabularyJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Concept => conceptJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.HistoricalAgent => historicalAgentJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.AuthoritativeSet => authoritativeSetJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.SystemEvent => systemEventJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Group => groupJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.UserProfile => userProfileJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Link => linkJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Annotation => annotationJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.PermissionGrant => permissionGrantJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.ContentType => contentTypeJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.AccessPoint => accessPointJson._clientFormat.asInstanceOf[Format[Model]]
      case EntityType.VirtualUnit => virtualUnitJson._clientFormat.asInstanceOf[Format[Model]]
    }

    def typeOf(json: JsValue): EntityType.Value = (json \ Entity.ISA).as(EnumUtils.enumReads(EntityType))

    implicit val clientReadAny: Reads[Model] = Reads { json =>
      _clientFormatRegistry
        .lift(typeOf(json))
        .map(json.validate(_))
        .getOrElse(
          JsError(JsPath(List(KeyPathNode(Entity.TYPE))),
            JsonValidationError(s"Unregistered Model type for Client read: ${typeOf(json)}")))
    }

    implicit val clientWriteAny: Writes[Model] = Writes { model =>
      _clientFormatRegistry
        .lift(model.isA)
        .map(Json.toJson(model)(_))
        .getOrElse {
          // FIXME: Throw an error here???
          logger.warn(s"Unregistered AnyModel type ${model.isA} (Writing to Client)")
          Json.toJson(Entity(id = model.id, `type` = model.isA, relationships = Map.empty))(Entity.entityFormat)
        }
    }

    implicit val _clientFormat: Format[Model] = Format(clientReadAny, clientWriteAny)
  }

  implicit object contentTypeJson extends ClientWriteable[DataContentType] {
    val _clientFormat: Format[DataContentType] = Json.format[DataContentType]
  }

  implicit object permissionGrantJson extends ClientWriteable[PermissionGrant] {
    private implicit val permissionGrantFormat: OFormat[models.PermissionGrantF] = Json.format[PermissionGrantF]
    implicit val _clientFormat: Format[PermissionGrant] = (
      JsPath.format(permissionGrantFormat) and
      (__ \ "accessor").lazyFormatNullable[Accessor](accessorJson._clientFormat) and
      (__ \ "targets").formatSeqOrEmpty(anyModelJson._clientFormat) and
      (__ \ "scope").lazyFormatNullable[Model](anyModelJson._clientFormat) and
      (__ \ "grantedBy").lazyFormatNullable[UserProfile](userProfileJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(PermissionGrant.apply, unlift(PermissionGrant.unapply))
  }

  implicit object accessPointJson extends ClientWriteable[AccessPoint] {
    val _clientFormat: Format[AccessPoint] = Json.format[AccessPoint]
  }

  implicit object linkJson extends ClientWriteable[Link] {
    private implicit val linkFormat: OFormat[models.LinkF] = Json.format[LinkF]
    val _clientFormat: Format[Link] = (
      JsPath.format[LinkF](linkFormat) and
      (__ \ "targets").formatSeqOrEmpty(anyModelJson._clientFormat) and
      (__ \ "source").lazyFormatNullable(anyModelJson._clientFormat) and
      (__ \ "user").lazyFormatNullable(accessorJson._clientFormat) and
      (__ \ "accessPoints").formatSeqOrEmpty(accessPointJson._clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Link.apply, unlift(Link.unapply))
  }

  implicit object countryJson extends ClientWriteable[Country] {

    private val _formFormat = Json.format[CountryF]
    val _clientFormat: Format[Country] = (
      JsPath.format[CountryF](_formFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Country.apply, unlift(Country.unapply))
  }

  implicit object versionJson extends ClientWriteable[Version] {
    private implicit val _formFormat: OFormat[models.VersionF] = Json.format[VersionF]
    implicit val _clientFormat: Format[Version] = (
      JsPath.format[VersionF](_formFormat) and
      (__ \ "event").lazyFormatNullable(systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Version.apply, unlift(Version.unapply))
  }

  implicit object accessorJson extends ClientWriteable[Accessor] {
    implicit val _reads: Reads[Accessor] = Reads(
      _.validate[Accessor](anyModelJson._clientFormat.asInstanceOf[Format[Accessor]]))

    implicit val _writes: Writes[Accessor] = Writes(
      Json.toJson(_)(anyModelJson._clientFormat.asInstanceOf[Format[Accessor]]))

    implicit val _clientFormat: Format[Accessor] = Format(_reads, _writes)
  }

  implicit object systemEventJson extends ClientWriteable[SystemEvent] {
    private implicit val _formFormat: OFormat[models.SystemEventF] = Json.format[SystemEventF]

    implicit val _clientFormat: Format[SystemEvent] = (
      JsPath.format[SystemEventF](_formFormat) and
      (__ \ "scope").lazyFormatNullable[Model](anyModelJson._clientFormat) and
      (__ \ "firstSubject").lazyFormatNullable[Model](anyModelJson._clientFormat) and
      (__ \ "user").lazyFormatNullable[Accessor](accessorJson._clientFormat) and
      (__ \ "version").lazyFormatNullable(versionJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(SystemEvent.apply, unlift(SystemEvent.unapply))
  }

  implicit object groupJson extends ClientWriteable[Group] {
    private lazy val _formFormat = Json.format[GroupF]
    lazy val _clientFormat: Format[Group] = (
      JsPath.format[GroupF](_formFormat) and
      (__ \ "groups").lazyNullableSeqFormat(_clientFormat) and
      (__ \ "accessibleTo").lazyNullableSeqFormat(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Group.apply, unlift(Group.unapply))
  }

  implicit object userProfileJson extends ClientWriteable[UserProfile] {
    private lazy val _formFormat = Json.format[UserProfileF]
    val _clientFormat: Format[UserProfile] = (
      JsPath.format[UserProfileF](_formFormat) and
      (__ \ "groups").formatSeqOrEmpty(groupJson._clientFormat) and
      (__ \ "accessibleTo").lazyNullableSeqFormat(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(UserProfile.quickApply, unlift(UserProfile.quickUnapply))
  }

  implicit object annotationJson extends ClientWriteable[Annotation] {
    private val _formFormat = Json.format[AnnotationF]
    val _clientFormat: Format[Annotation] = (
      JsPath.format[AnnotationF](_formFormat) and
      (__ \ "annotations").lazyNullableSeqFormat(_clientFormat) and
      (__ \ "user").lazyFormatNullable[UserProfile](userProfileJson._clientFormat) and
      (__ \ "source").lazyFormatNullable[Model](anyModelJson._clientFormat) and
      (__ \ "target").lazyFormatNullable[Model](anyModelJson._clientFormat) and
      (__ \ "targetPart").lazyFormatNullable[Entity](Entity.entityFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Annotation.apply, unlift(Annotation.unapply))
  }

  implicit object documentaryUnitDescriptionJson extends ClientWriteable[DocumentaryUnitDescriptionF] {
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson._clientFormat
    private implicit val datePeriodFormat: Format[models.DatePeriodF] = datePeriodJson._clientFormat
    private implicit val isadGIdentityFormat: OFormat[models.IsadGIdentity] = Json.format[IsadGIdentity]
    private implicit val isadGContextFormat: OFormat[models.IsadGContext] = Json.format[IsadGContext]
    private implicit val isadGContentFormat: OFormat[models.IsadGContent] = Json.format[IsadGContent]
    private implicit val isadGConditionsFormat: OFormat[models.IsadGConditions] = Json.format[IsadGConditions]
    private implicit val isadGMaterialsFormat: OFormat[models.IsadGMaterials] = Json.format[IsadGMaterials]
    private implicit val isadGControlFormat: OFormat[models.IsadGControl] = Json.format[IsadGControl]
    val _clientFormat: Format[DocumentaryUnitDescriptionF] = Json.format[DocumentaryUnitDescriptionF]
  }

  implicit object historicalAgentDescriptionJson extends ClientWriteable[HistoricalAgentDescriptionF] {
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson._clientFormat
    private implicit val datePeriodFormat: Format[models.DatePeriodF] = datePeriodJson._clientFormat
    private implicit val isaarDetailsFormat: OFormat[models.IsaarDetail] = Json.format[IsaarDetail]
    private implicit val isaarControlFormat: OFormat[models.IsaarControl] = Json.format[IsaarControl]
    val _clientFormat: Format[HistoricalAgentDescriptionF] = Json.format[HistoricalAgentDescriptionF]
  }

  implicit object repositoryDescriptionJson extends ClientWriteable[RepositoryDescriptionF] {
    private implicit val addressFormat: Writes[models.AddressF] = addressJson._clientFormat
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson._clientFormat
    private implicit val isdiahDetailsFormat: OFormat[models.IsdiahDetails] = Json.format[IsdiahDetails]
    private implicit val isdiahAccessFormat: OFormat[models.IsdiahAccess] = Json.format[IsdiahAccess]
    private implicit val isdiahServicesFormat: OFormat[models.IsdiahServices] = Json.format[IsdiahServices]
    private implicit val isdiahControlFormat: OFormat[models.IsdiahControl] = Json.format[IsdiahControl]
    val _clientFormat: Format[RepositoryDescriptionF] = Json.format[RepositoryDescriptionF]
  }

  implicit object conceptDescriptionJson extends ClientWriteable[ConceptDescriptionF] {
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson._clientFormat
    lazy val _clientFormat: Format[ConceptDescriptionF] = Json.format[ConceptDescriptionF]
  }

  implicit object historicalAgentJson extends ClientWriteable[HistoricalAgent] {
    private implicit val haDescFmt: Format[models.HistoricalAgentDescriptionF] = historicalAgentDescriptionJson._clientFormat
    private val _formFormat = Json.format[HistoricalAgentF]

    val _clientFormat: Format[HistoricalAgent] = (
      JsPath.format(_formFormat) and
      (__ \ "set").formatNullable[AuthoritativeSet](authoritativeSetJson._clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(HistoricalAgent.apply, unlift(HistoricalAgent.unapply))
  }

  implicit object repositoryJson extends ClientWriteable[Repository] {
    private implicit val repoDescFmt: Format[models.RepositoryDescriptionF] = repositoryDescriptionJson._clientFormat
    private val _formFormat = Json.format[RepositoryF]

    val _clientFormat: Format[Repository] = (
      JsPath.format(_formFormat) and
      (__ \ "country").formatNullable[Country](countryJson._clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Repository.apply, unlift(Repository.unapply))
  }

  implicit object documentaryUnitJson extends ClientWriteable[DocumentaryUnit] {
    private implicit val docDescFmt: Format[models.DocumentaryUnitDescriptionF] = documentaryUnitDescriptionJson._clientFormat
    private val _formFormat = Json.format[DocumentaryUnitF]
    lazy val _clientFormat: Format[DocumentaryUnit] = (
      JsPath.format(_formFormat) and
      (__ \ "holder").formatNullable[Repository](repositoryJson._clientFormat) and
      (__ \ "parent").lazyFormatNullable[DocumentaryUnit](_clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(DocumentaryUnit.apply, unlift(DocumentaryUnit.unapply))
  }

  implicit object virtualUnitJson extends ClientWriteable[VirtualUnit] {
    private implicit val _vuClientFormat: Format[models.DocumentaryUnitDescriptionF] = documentaryUnitDescriptionJson._clientFormat
    private val _formFormat = Json.format[VirtualUnitF]

    lazy val _clientFormat: Format[VirtualUnit] = (
      JsPath.format[VirtualUnitF](_formFormat) and
      (__ \ "descriptions").formatSeqOrEmpty(documentaryUnitJson._clientFormat) and
      (__ \ "author").formatNullable[Accessor](accessorJson._clientFormat) and
      (__ \ "parent").lazyFormatNullable[VirtualUnit](_clientFormat) and
      (__ \ "holder").formatNullable[Repository](repositoryJson._clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(VirtualUnit.apply, unlift(VirtualUnit.unapply))
  }

  implicit object authoritativeSetJson extends ClientWriteable[AuthoritativeSet] {
    private val _formFormat = Json.format[AuthoritativeSetF]
    val _clientFormat: Format[AuthoritativeSet] = (
      JsPath.format[AuthoritativeSetF](_formFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(AuthoritativeSet.apply, unlift(AuthoritativeSet.unapply))
  }

  implicit object vocabularyJson extends ClientWriteable[Vocabulary] {
    private val _formFormat = Json.format[VocabularyF]
    val _clientFormat: Format[Vocabulary] = (
      JsPath.format[VocabularyF](_formFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Vocabulary.apply, unlift(Vocabulary.unapply))
  }

  implicit object conceptJson extends ClientWriteable[Concept] {

    private implicit val fdFormat: Format[models.ConceptDescriptionF] = conceptDescriptionJson._clientFormat
    implicit val _formFormat: Format[ConceptF] = Json.format[ConceptF]
    val _clientFormat: Format[Concept] = (
      JsPath.format[ConceptF](_formFormat) and
      (__ \ "vocabulary").formatNullable[Vocabulary](vocabularyJson._clientFormat) and
      (__ \ "parent").lazyFormatNullable[Concept](_clientFormat) and
      (__ \ "broaderTerms").lazyFormat[Seq[Concept]](Format(Reads.seq(_clientFormat), Writes.seq(_clientFormat))) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson._clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson._clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Concept.apply, unlift(Concept.unapply))
  }
}
