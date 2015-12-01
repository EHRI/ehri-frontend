package client

import models._
import play.api.libs.json._
import models.base.{AnyModel, Accessor}
import play.api.libs.functional.syntax._
import models.json._
import defines.EntityType
import play.api.libs.json.KeyPathNode
import play.api.libs.json.JsObject
import play.api.data.validation.ValidationError
import play.api.Logger
import backend.Entity

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
package object json {

  implicit object datePeriodJson extends ClientWriteable[DatePeriodF] {
    lazy val clientFormat = Json.format[DatePeriodF]
  }

  implicit object addressJson extends ClientWriteable[AddressF] {
    lazy val clientFormat = Json.format[AddressF]
  }

  implicit object anyModelJson extends ClientWriteable[AnyModel] {
    private val clientFormatRegistry: PartialFunction[EntityType.Value, Format[AnyModel]] = {
      case EntityType.Repository => repositoryJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.Country => countryJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.DocumentaryUnit => documentaryUnitJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.Vocabulary => vocabularyJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.Concept => conceptJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.HistoricalAgent => historicalAgentJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.AuthoritativeSet => authoritativeSetJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.SystemEvent => systemEventJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.Group => groupJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.UserProfile => userProfileJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.Link => linkJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.Annotation => annotationJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.PermissionGrant => permissionGrantJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.ContentType => contentTypeJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.AccessPoint => accessPointJson.clientFormat.asInstanceOf[Format[AnyModel]]
      case EntityType.VirtualUnit => virtualUnitJson.clientFormat.asInstanceOf[Format[AnyModel]]
    }

    implicit val clientFormat: Format[AnyModel] = new Format[AnyModel] {
      def reads(json: JsValue): JsResult[AnyModel] = {
        val et = (json \ Entity.TYPE).as(defines.EnumUtils.enumReads(EntityType))
        clientFormatRegistry.lift(et).map { format =>
          json.validate(format)
        } getOrElse {
          JsError(JsPath(List(KeyPathNode(Entity.TYPE))), ValidationError("Unregistered AnyModel type for Client read: " + et))
        }
      }


      def writes(a: AnyModel): JsValue = {
        clientFormatRegistry.lift(a.isA).map { format =>
          Json.toJson(a)(format)
        } getOrElse {
          // FIXME: Throw an error here???
          Logger.logger.warn("Unregistered AnyModel type {} (Writing to Client)", a.isA)
          Json.toJson(Entity(id = a.id, `type` = a.isA, relationships = Map.empty))(Entity.entityFormat)
        }
      }
    }
  }

  implicit object contentTypeJson extends ClientWriteable[ContentType] {
    val clientFormat = Json.format[ContentType]
  }

  implicit object permissionGrantJson extends ClientWriteable[PermissionGrant] {
    private implicit val permissionGrantFormat = Json.format[PermissionGrantF]
    implicit val clientFormat: Format[PermissionGrant] = (
      JsPath.format(permissionGrantFormat) and
        (__ \ "accessor").lazyFormatNullable[Accessor](accessorJson.clientFormat) and
        (__ \ "targets").nullableSeqFormat(anyModelJson.clientFormat) and
        (__ \ "scope").lazyFormatNullable[AnyModel](anyModelJson.clientFormat) and
        (__ \ "grantedBy").lazyFormatNullable[UserProfile](userProfileJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(PermissionGrant.apply, unlift(PermissionGrant.unapply))
  }

  implicit object accessPointJson extends ClientWriteable[AccessPoint] {
    // This hassle necessary because single-field case classes require special handling,
    // see: http://stackoverflow.com/a/17282296/285374
    // private implicit val accessPointFormat = Json.format[AccessPointF]
    // private val clr: Reads[AccessPoint] = __.read[AccessPointF].map(AccessPoint.apply(_))
    // private val clw: Writes[AccessPoint] = __.write[AccessPointF].contramap(_.model)
    val clientFormat = Json.format[AccessPoint]
  }

  implicit object linkJson extends ClientWriteable[Link] {
    private implicit val linkFormat = Json.format[LinkF]
    val clientFormat: Format[Link] = (
      JsPath.format[LinkF](linkFormat) and
        (__ \ "targets").nullableSeqFormat(anyModelJson.clientFormat) and
        (__ \ "user").lazyFormatNullable[UserProfile](userProfileJson.clientFormat) and
        (__ \ "accessPoints").nullableSeqFormat(accessPointJson.clientFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "promotedBy").nullableSeqFormat(userProfileJson.clientFormat) and
        (__ \ "demotedBy").nullableSeqFormat(userProfileJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Link.apply, unlift(Link.unapply))
  }

  implicit object countryJson extends ClientWriteable[Country] {

    private val fFormat = Json.format[CountryF]
    val clientFormat: Format[Country] = (
      JsPath.format[CountryF](fFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Country.apply, unlift(Country.unapply))
  }

  implicit object versionJson extends ClientWriteable[Version] {
    private implicit val fFormat = Json.format[VersionF]
    implicit val clientFormat: Format[Version] = (
      JsPath.format[VersionF](fFormat) and
        (__ \ "event").lazyFormatNullable(systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Version.apply, unlift(Version.unapply))
  }

  implicit object accessorJson extends ClientWriteable[Accessor] {
    implicit val clientFormat: Format[Accessor] = new Format[Accessor] {
      def reads(json: JsValue): JsResult[Accessor] = {
        json.validate[Accessor](anyModelJson.clientFormat.asInstanceOf[Format[Accessor]])
      }

      def writes(a: Accessor): JsValue = {
        Json.toJson(a)(anyModelJson.clientFormat.asInstanceOf[Format[Accessor]])
      }
    }
  }

  implicit object systemEventJson extends ClientWriteable[SystemEvent] {
    private implicit val fFormat = Json.format[SystemEventF]

    implicit val clientFormat: Format[SystemEvent] = (
      JsPath.format[SystemEventF](fFormat) and
        (__ \ "scope").lazyFormatNullable[AnyModel](anyModelJson.clientFormat) and
        (__ \ "firstSubject").lazyFormatNullable[AnyModel](anyModelJson.clientFormat) and
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
        (__ \ "groups").nullableSeqFormat(groupJson.clientFormat) and
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
        (__ \ "source").lazyFormatNullable[AnyModel](anyModelJson.clientFormat) and
        (__ \ "target").lazyFormatNullable[AnyModel](anyModelJson.clientFormat) and
        (__ \ "targetPart").lazyFormatNullable[Entity](Entity.entityFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "promotedBy").nullableSeqFormat(userProfileJson.clientFormat) and
        (__ \ "demotedBy").nullableSeqFormat(userProfileJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Annotation.apply, unlift(Annotation.unapply))
  }

  implicit object documentaryUnitDescriptionJson extends ClientWriteable[DocumentaryUnitDescriptionF] {
    private implicit val accessPointFormat = accessPointJson.clientFormat
    private implicit val datePeriodFormat = datePeriodJson.clientFormat
    private implicit val isadGIdentityFormat = Json.format[IsadGIdentity]
    private implicit val isadGContextFormat = Json.format[IsadGContext]
    private implicit val isadGContentFormat = Json.format[IsadGContent]
    private implicit val isadGConditionsFormat = Json.format[IsadGConditions]
    private implicit val isadGMaterialsFormat = Json.format[IsadGMaterials]
    private implicit val isadGControlFormat = Json.format[IsadGControl]
    val clientFormat = Json.format[DocumentaryUnitDescriptionF]
  }

  implicit object historicalAgentDescriptionJson extends ClientWriteable[HistoricalAgentDescriptionF] {
    private implicit val accessPointFormat = accessPointJson.clientFormat
    private implicit val datePeriodFormat = datePeriodJson.clientFormat
    private implicit val isaarDetailsFormat = Json.format[IsaarDetail]
    private implicit val isaarControlFormat = Json.format[IsaarControl]
    val clientFormat = Json.format[HistoricalAgentDescriptionF]
  }

  implicit object repositoryDescriptionJson extends ClientWriteable[RepositoryDescriptionF] {
    private implicit val addressFormat = addressJson.clientFormat
    private implicit val accessPointFormat = accessPointJson.clientFormat
    private implicit val isdiahDetailsFormat = Json.format[IsdiahDetails]
    private implicit val isdiahAccessFormat = Json.format[IsdiahAccess]
    private implicit val isdiahServicesFormat = Json.format[IsdiahServices]
    private implicit val isdiahControlFormat = Json.format[IsdiahControl]
    val clientFormat = Json.format[RepositoryDescriptionF]
  }

  implicit object conceptDescriptionJson extends ClientWriteable[ConceptDescriptionF] {
    private implicit val accessPointFormat = accessPointJson.clientFormat
    lazy val clientFormat = Json.format[ConceptDescriptionF]
  }

  implicit object historicalAgentJson extends ClientWriteable[HistoricalAgent] {
    private implicit val haDescFmt = historicalAgentDescriptionJson.clientFormat
    private val fFormat = Json.format[HistoricalAgentF]

    val clientFormat: Format[HistoricalAgent] = (
      JsPath.format(fFormat) and
        (__ \ "set").formatNullable[AuthoritativeSet](authoritativeSetJson.clientFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(HistoricalAgent.apply, unlift(HistoricalAgent.unapply))
  }

  implicit object repositoryJson extends ClientWriteable[Repository] {
    private implicit val repoDescFmt = repositoryDescriptionJson.clientFormat
    private val fFormat = Json.format[RepositoryF]

    val clientFormat: Format[Repository] = (
      JsPath.format(fFormat) and
        (__ \ "country").formatNullable[Country](countryJson.clientFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Repository.apply, unlift(Repository.unapply))
  }

  implicit object documentaryUnitJson extends ClientWriteable[DocumentaryUnit] {
    private implicit val docDescFmt = documentaryUnitDescriptionJson.clientFormat
    private val fFormat = Json.format[DocumentaryUnitF]
    lazy val clientFormat: Format[DocumentaryUnit] = (
      JsPath.format(fFormat) and
        (__ \ "holder").formatNullable[Repository](repositoryJson.clientFormat) and
        (__ \ "parent").lazyFormatNullable[DocumentaryUnit](clientFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(DocumentaryUnit.apply, unlift(DocumentaryUnit.unapply))
  }

  implicit object virtualUnitJson extends ClientWriteable[VirtualUnit] {
    private implicit val vuDescFmt = documentaryUnitDescriptionJson.clientFormat
    private val fFormat = Json.format[VirtualUnitF]

    lazy val clientFormat: Format[VirtualUnit] = (
      JsPath.format[VirtualUnitF](fFormat) and
        (__ \ "descriptions").nullableSeqFormat(documentaryUnitJson.clientFormat) and
        (__ \ "author").formatNullable[Accessor](accessorJson.clientFormat) and
        (__ \ "parent").lazyFormatNullable[VirtualUnit](clientFormat) and
        (__ \ "holder").formatNullable[Repository](repositoryJson.clientFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(VirtualUnit.apply, unlift(VirtualUnit.unapply))
  }

  implicit object authoritativeSetJson extends ClientWriteable[AuthoritativeSet] {
    private val fFormat = Json.format[AuthoritativeSetF]
    val clientFormat: Format[AuthoritativeSet] = (
      JsPath.format[AuthoritativeSetF](fFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(AuthoritativeSet.apply, unlift(AuthoritativeSet.unapply))
  }

  implicit object vocabularyJson extends ClientWriteable[Vocabulary] {
    private val fFormat = Json.format[VocabularyF]
    val clientFormat: Format[Vocabulary] = (
      JsPath.format[VocabularyF](fFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Vocabulary.apply, unlift(Vocabulary.unapply))
  }

  implicit object conceptJson extends ClientWriteable[Concept] {

    private implicit val fFormat = Json.format[ConceptF]
    val clientFormat: Format[Concept] = (
      JsPath.format[ConceptF](fFormat) and
        (__ \ "vocabulary").formatNullable[Vocabulary](vocabularyJson.clientFormat) and
        (__ \ "parent").lazyFormatNullable[Concept](clientFormat) and
        (__ \ "broaderTerms").lazyNullableSeqFormat(clientFormat) and
        (__ \ "accessibleTo").nullableSeqFormat(accessorJson.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](systemEventJson.clientFormat) and
        (__ \ "meta").format[JsObject]
      )(Concept.apply, unlift(Concept.unapply))
  }
}
