package client

import models.json._
import models._
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.EnumUtils

package object json {

  implicit object datePeriodJson extends ClientWriteable[DatePeriodF] {
    lazy val clientFormat: Format[DatePeriodF] = Json.format[DatePeriodF]
  }

  implicit object addressJson extends ClientWriteable[AddressF] {
    lazy val clientFormat: Writes[AddressF] = Json.writes[AddressF]
  }

  implicit object anyModelJson extends ClientWriteable[Model] {
    private val logger = Logger(getClass)
    private val clientFormatRegistry: PartialFunction[EntityType.Value, Format[Model]] = {
      case EntityType.Repository => repositoryJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Country => countryJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.DocumentaryUnit => documentaryUnitJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Vocabulary => vocabularyJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Concept => conceptJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.HistoricalAgent => historicalAgentJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.AuthoritativeSet => authoritativeSetJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.SystemEvent => systemEventJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Group => groupJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.UserProfile => userProfileJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Link => linkJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.Annotation => annotationJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.PermissionGrant => permissionGrantJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.ContentType => contentTypeJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.AccessPoint => accessPointJson.clientFormat.asInstanceOf[Format[Model]]
      case EntityType.VirtualUnit => virtualUnitJson.clientFormat.asInstanceOf[Format[Model]]
    }

    def typeOf(json: JsValue): EntityType.Value = (json \ Entity.ISA).as(EnumUtils.enumReads(EntityType))

    implicit val clientReadAny: Reads[Model] = Reads { json =>
      clientFormatRegistry
        .lift(typeOf(json))
        .map(json.validate(_))
        .getOrElse(
          JsError(JsPath(List(KeyPathNode(Entity.TYPE))),
            JsonValidationError(s"Unregistered Model type for Client read: ${typeOf(json)}")))
    }

    implicit val clientWriteAny: Writes[Model] = Writes { model =>
      clientFormatRegistry
        .lift(model.isA)
        .map(Json.toJson(model)(_))
        .getOrElse {
          // FIXME: Throw an error here???
          logger.warn(s"Unregistered AnyModel type ${model.isA} (Writing to Client)")
          Json.toJson(Entity(id = model.id, `type` = model.isA, relationships = Map.empty))(Entity.entityFormat)
        }
    }

    implicit val clientFormat: Format[Model] = Format(clientReadAny, clientWriteAny)
  }

  implicit object contentTypeJson extends ClientWriteable[DataContentType] {
    val clientFormat: Format[DataContentType] = Json.format[DataContentType]
  }

  implicit object permissionGrantJson extends ClientWriteable[PermissionGrant] {
    private implicit val permissionGrantFormat: OFormat[models.PermissionGrantF] = Json.format[PermissionGrantF]
    implicit val clientFormat: Format[PermissionGrant] = (
      JsPath.format(permissionGrantFormat) and
      (__ \ "accessor").lazyFormatNullable[Accessor](accessorJson.clientFormat) and
      (__ \ "targets").formatSeqOrEmpty(anyModelJson.clientFormat) and
      (__ \ "scope").lazyFormatNullable[Model](anyModelJson.clientFormat) and
      (__ \ "grantedBy").lazyFormatNullable[UserProfile](userProfileJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(PermissionGrant.apply, unlift(PermissionGrant.unapply))
  }

  implicit object accessPointJson extends ClientWriteable[AccessPoint] {
    val clientFormat: Format[AccessPoint] = Json.format[AccessPoint]
  }

  implicit object linkJson extends ClientWriteable[Link] {
    private implicit val linkFormat: OFormat[models.LinkF] = Json.format[LinkF]
    val clientFormat: Format[Link] = (
      JsPath.format[LinkF](linkFormat) and
      (__ \ "targets").formatSeqOrEmpty(anyModelJson.clientFormat) and
      (__ \ "source").lazyFormatNullable(anyModelJson.clientFormat) and
      (__ \ "user").lazyFormatNullable(accessorJson.clientFormat) and
      (__ \ "accessPoints").formatSeqOrEmpty(accessPointJson.clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Link.apply, unlift(Link.unapply))
  }

  implicit object countryJson extends ClientWriteable[Country] {

    private val fFormat = Json.format[CountryF]
    val clientFormat: Format[Country] = (
      JsPath.format[CountryF](fFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Country.apply, unlift(Country.unapply))
  }

  implicit object versionJson extends ClientWriteable[Version] {
    private implicit val fFormat: OFormat[models.VersionF] = Json.format[VersionF]
    implicit val clientFormat: Format[Version] = (
      JsPath.format[VersionF](fFormat) and
      (__ \ "event").lazyFormatNullable(systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Version.apply, unlift(Version.unapply))
  }

  implicit object accessorJson extends ClientWriteable[Accessor] {
    implicit val reads: Reads[Accessor] = Reads(
      _.validate[Accessor](anyModelJson.clientFormat.asInstanceOf[Format[Accessor]]))

    implicit val writes: Writes[Accessor] = Writes(
      Json.toJson(_)(anyModelJson.clientFormat.asInstanceOf[Format[Accessor]]))

    implicit val clientFormat: Format[Accessor] = Format(reads, writes)
  }

  implicit object systemEventJson extends ClientWriteable[SystemEvent] {
    private implicit val fFormat: OFormat[models.SystemEventF] = Json.format[SystemEventF]

    implicit val clientFormat: Format[SystemEvent] = (
      JsPath.format[SystemEventF](fFormat) and
      (__ \ "scope").lazyFormatNullable[Model](anyModelJson.clientFormat) and
      (__ \ "firstSubject").lazyFormatNullable[Model](anyModelJson.clientFormat) and
      (__ \ "user").lazyFormatNullable[Accessor](accessorJson.clientFormat) and
      (__ \ "version").lazyFormatNullable(versionJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(SystemEvent.apply, unlift(SystemEvent.unapply))
  }

  implicit object groupJson extends ClientWriteable[Group] {
    private lazy val fFormat = Json.format[GroupF]
    lazy val clientFormat: Format[Group] = (
      JsPath.format[GroupF](fFormat) and
      (__ \ "groups").lazyNullableSeqFormat(clientFormat) and
      (__ \ "accessibleTo").lazyNullableSeqFormat(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Group.apply, unlift(Group.unapply))
  }

  implicit object userProfileJson extends ClientWriteable[UserProfile] {
    private lazy val fFormat = Json.format[UserProfileF]
    val clientFormat: Format[UserProfile] = (
      JsPath.format[UserProfileF](fFormat) and
      (__ \ "groups").formatSeqOrEmpty(groupJson.clientFormat) and
      (__ \ "accessibleTo").lazyNullableSeqFormat(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(UserProfile.quickApply, unlift(UserProfile.quickUnapply))
  }

  implicit object annotationJson extends ClientWriteable[Annotation] {
    private val fFormat = Json.format[AnnotationF]
    val clientFormat: Format[Annotation] = (
      JsPath.format[AnnotationF](fFormat) and
      (__ \ "annotations").lazyNullableSeqFormat(clientFormat) and
      (__ \ "user").lazyFormatNullable[UserProfile](userProfileJson.clientFormat) and
      (__ \ "source").lazyFormatNullable[Model](anyModelJson.clientFormat) and
      (__ \ "target").lazyFormatNullable[Model](anyModelJson.clientFormat) and
      (__ \ "targetPart").lazyFormatNullable[Entity](Entity.entityFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Annotation.apply, unlift(Annotation.unapply))
  }

  implicit object documentaryUnitDescriptionJson extends ClientWriteable[DocumentaryUnitDescriptionF] {
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson.clientFormat
    private implicit val datePeriodFormat: Format[models.DatePeriodF] = datePeriodJson.clientFormat
    private implicit val isadGIdentityFormat: OFormat[models.IsadGIdentity] = Json.format[IsadGIdentity]
    private implicit val isadGContextFormat: OFormat[models.IsadGContext] = Json.format[IsadGContext]
    private implicit val isadGContentFormat: OFormat[models.IsadGContent] = Json.format[IsadGContent]
    private implicit val isadGConditionsFormat: OFormat[models.IsadGConditions] = Json.format[IsadGConditions]
    private implicit val isadGMaterialsFormat: OFormat[models.IsadGMaterials] = Json.format[IsadGMaterials]
    private implicit val isadGControlFormat: OFormat[models.IsadGControl] = Json.format[IsadGControl]
    val clientFormat: Format[DocumentaryUnitDescriptionF] = Json.format[DocumentaryUnitDescriptionF]
  }

  implicit object historicalAgentDescriptionJson extends ClientWriteable[HistoricalAgentDescriptionF] {
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson.clientFormat
    private implicit val datePeriodFormat: Format[models.DatePeriodF] = datePeriodJson.clientFormat
    private implicit val isaarDetailsFormat: OFormat[models.IsaarDetail] = Json.format[IsaarDetail]
    private implicit val isaarControlFormat: OFormat[models.IsaarControl] = Json.format[IsaarControl]
    val clientFormat: Format[HistoricalAgentDescriptionF] = Json.format[HistoricalAgentDescriptionF]
  }

  implicit object repositoryDescriptionJson extends ClientWriteable[RepositoryDescriptionF] {
    private implicit val addressFormat: Writes[models.AddressF] = addressJson.clientFormat
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson.clientFormat
    private implicit val isdiahDetailsFormat: OFormat[models.IsdiahDetails] = Json.format[IsdiahDetails]
    private implicit val isdiahAccessFormat: OFormat[models.IsdiahAccess] = Json.format[IsdiahAccess]
    private implicit val isdiahServicesFormat: OFormat[models.IsdiahServices] = Json.format[IsdiahServices]
    private implicit val isdiahControlFormat: OFormat[models.IsdiahControl] = Json.format[IsdiahControl]
    val clientFormat: Format[RepositoryDescriptionF] = Json.format[RepositoryDescriptionF]
  }

  implicit object conceptDescriptionJson extends ClientWriteable[ConceptDescriptionF] {
    private implicit val accessPointFormat: Format[models.AccessPoint] = accessPointJson.clientFormat
    lazy val clientFormat: Format[ConceptDescriptionF] = Json.format[ConceptDescriptionF]
  }

  implicit object historicalAgentJson extends ClientWriteable[HistoricalAgent] {
    private implicit val haDescFmt: Format[models.HistoricalAgentDescriptionF] = historicalAgentDescriptionJson.clientFormat
    private val fFormat = Json.format[HistoricalAgentF]

    val clientFormat: Format[HistoricalAgent] = (
      JsPath.format(fFormat) and
      (__ \ "set").formatNullable[AuthoritativeSet](authoritativeSetJson.clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(HistoricalAgent.apply, unlift(HistoricalAgent.unapply))
  }

  implicit object repositoryJson extends ClientWriteable[Repository] {
    private implicit val repoDescFmt: Format[models.RepositoryDescriptionF] = repositoryDescriptionJson.clientFormat
    private val fFormat = Json.format[RepositoryF]

    val clientFormat: Format[Repository] = (
      JsPath.format(fFormat) and
      (__ \ "country").formatNullable[Country](countryJson.clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Repository.apply, unlift(Repository.unapply))
  }

  implicit object documentaryUnitJson extends ClientWriteable[DocumentaryUnit] {
    private implicit val docDescFmt: Format[models.DocumentaryUnitDescriptionF] = documentaryUnitDescriptionJson.clientFormat
    private val fFormat = Json.format[DocumentaryUnitF]
    lazy val clientFormat: Format[DocumentaryUnit] = (
      JsPath.format(fFormat) and
      (__ \ "holder").formatNullable[Repository](repositoryJson.clientFormat) and
      (__ \ "parent").lazyFormatNullable[DocumentaryUnit](clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(DocumentaryUnit.apply, unlift(DocumentaryUnit.unapply))
  }

  implicit object virtualUnitJson extends ClientWriteable[VirtualUnit] {
    private implicit val vuDescFmt: Format[models.DocumentaryUnitDescriptionF] = documentaryUnitDescriptionJson.clientFormat
    private val fFormat = Json.format[VirtualUnitF]

    lazy val clientFormat: Format[VirtualUnit] = (
      JsPath.format[VirtualUnitF](fFormat) and
      (__ \ "descriptions").formatSeqOrEmpty(documentaryUnitJson.clientFormat) and
      (__ \ "author").formatNullable[Accessor](accessorJson.clientFormat) and
      (__ \ "parent").lazyFormatNullable[VirtualUnit](clientFormat) and
      (__ \ "holder").formatNullable[Repository](repositoryJson.clientFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(VirtualUnit.apply, unlift(VirtualUnit.unapply))
  }

  implicit object authoritativeSetJson extends ClientWriteable[AuthoritativeSet] {
    private val fFormat = Json.format[AuthoritativeSetF]
    val clientFormat: Format[AuthoritativeSet] = (
      JsPath.format[AuthoritativeSetF](fFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(AuthoritativeSet.apply, unlift(AuthoritativeSet.unapply))
  }

  implicit object vocabularyJson extends ClientWriteable[Vocabulary] {
    private val fFormat = Json.format[VocabularyF]
    val clientFormat: Format[Vocabulary] = (
      JsPath.format[VocabularyF](fFormat) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "promotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "demotedBy").formatSeqOrEmpty(userProfileJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Vocabulary.apply, unlift(Vocabulary.unapply))
  }

  implicit object conceptJson extends ClientWriteable[Concept] {

    private implicit val fdFormat: Format[models.ConceptDescriptionF] = conceptDescriptionJson.clientFormat
    implicit val fFormat: Format[ConceptF] = Json.format[ConceptF]
    val clientFormat: Format[Concept] = (
      JsPath.format[ConceptF](fFormat) and
      (__ \ "vocabulary").formatNullable[Vocabulary](vocabularyJson.clientFormat) and
      (__ \ "parent").lazyFormatNullable[Concept](clientFormat) and
      (__ \ "broaderTerms").lazyFormat[Seq[Concept]](Format(Reads.seq(clientFormat), Writes.seq(clientFormat))) and
      (__ \ "accessibleTo").formatSeqOrEmpty(accessorJson.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(Concept.apply, unlift(Concept.unapply))
  }
}
