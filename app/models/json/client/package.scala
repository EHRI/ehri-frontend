package models.json

import models._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import models.base.{MetaModel, Accessor}

/**
 * User: michaelb
 * 
 * Json formats for client. These are auto-generated by the framework
 * macro and simply match the case classes.
 */
package object client {

  // NB: Since models depend on each other, order or declaration
  // is significant.

  implicit val accessPointFormat = Json.format[AccessPointF]
  implicit val addressFormat = Json.format[AddressF]
  implicit val annotationFormat = Json.format[AnnotationF]
  implicit val datePeriodFormat = Json.format[DatePeriodF]

  private implicit val isaarDetailsFormat = Json.format[IsaarDetail]
  private implicit val isaarControlFormat = Json.format[IsaarControl]
  implicit val isaarFormat = Json.format[HistoricalAgentDescriptionF]

  private implicit val isadGContextFormat = Json.format[IsadGContext]
  private implicit val isadGContentFormat = Json.format[IsadGContent]
  private implicit val isadGConditionsFormat = Json.format[IsadGConditions]
  private implicit val isadGMaterialsFormat = Json.format[IsadGMaterials]
  private implicit val isadGControlFormat = Json.format[IsadGControl]
  implicit val isadGFormat = Json.format[DocumentaryUnitDescriptionF]

  private implicit val isdiahDetailsFormat = Json.format[IsdiahDetails]
  private implicit val isdiahAccessFormat = Json.format[IsdiahAccess]
  private implicit val isdiahServicesFormat = Json.format[IsdiahServices]
  private implicit val isdiahControlFormat = Json.format[IsdiahControl]
  implicit val isdiahFormat = Json.format[RepositoryDescriptionF]

  implicit val authoritativeSetFormat = Json.format[AuthoritativeSetF]
  implicit val conceptFormat = Json.format[ConceptF]
  implicit val conceptDescriptionFormat = Json.format[ConceptDescriptionF]
  implicit val countryFormat = Json.format[CountryF]
  implicit val documentaryUnitFormat = Json.format[DocumentaryUnitF]
  implicit val groupFormat = Json.format[GroupF]
  implicit val historicalAgentFormat = Json.format[HistoricalAgentF]

  implicit val linkFormat = Json.format[LinkF]
  implicit val repositoryFormat = Json.format[RepositoryF]
  implicit val userProfileFormat = Json.format[UserProfileF]
  implicit val vocabularyFormat = Json.format[VocabularyF]

  implicit val systemEventFormat = Json.format[SystemEventF]
  implicit val permissionGrantFormat = Json.format[PermissionGrantF]

  // Meta models. These are defined manually because we want to
  // place their model fields (as opposed to the metadata) at the
  // top level to make the JSON neater.
  
  implicit val systemEventMetaFormat: Format[SystemEventMeta] = (
    __.format[SystemEventF] and
    (__ \ "scope").lazyFormatNullable[MetaModel[_]](MetaModel.Converter.clientFormat) and
    (__ \ "user").lazyFormatNullable[UserProfileMeta](userProfileMetaFormat)
  )(SystemEventMeta.apply _, unlift(SystemEventMeta.unapply _))

  private implicit val Accessor.Converter.clientFormat = Accessor.Converter.clientFormat
  private val accessorListFormat = Format(Reads.list[Accessor](Accessor.Converter.clientFormat), Writes.list[Accessor](Accessor.Converter.clientFormat))

  implicit val userProfileMetaFormat: Format[UserProfileMeta] = (
    __.format[UserProfileF] and
      (__ \ "groups").lazyFormat[List[GroupMeta]](Reads.list[GroupMeta], Writes.list[GroupMeta]) and
      (__ \ "accessibleTo").lazyFormat[List[Accessor]](accessorListFormat) and
      (__ \ "event").formatNullable[SystemEventMeta]
    )(UserProfileMeta.quickApply _, unlift(UserProfileMeta.quickUnapply _))

  implicit val linkMetaFormat: Format[LinkMeta] = (
    __.format[LinkF] and
      (__ \ "targets").lazyFormat[List[MetaModel[_]]](Reads.list[MetaModel[_]](MetaModel.Converter.clientFormat), Writes.list[MetaModel[_]](MetaModel.Converter.clientFormat)) and
      (__ \ "user").lazyFormatNullable[UserProfileMeta](userProfileMetaFormat) and
      (__ \ "accessPoints").lazyFormatNullable[List[AccessPointF]](Reads.list(accessPointFormat), Writes.list(accessPointFormat)).flatMap(_.headOption) and
      (__ \ "accessibleTo").lazyFormat[List[Accessor]](accessorListFormat) and
      (__ \ "event").formatNullable[SystemEventMeta]
    )(LinkMeta.apply _, unlift(LinkMeta.unapply _))

  implicit val annotationMetaFormat: Format[AnnotationMeta] = (
    __.format[AnnotationF] and
      (__ \ "annotations").lazyFormat[List[AnnotationMeta]](Reads.list[AnnotationMeta], Writes.list[AnnotationMeta]) and
      (__ \ "user").lazyFormatNullable[UserProfileMeta](userProfileMetaFormat) and
      (__ \ "source").lazyFormatNullable[MetaModel[_]](MetaModel.Converter.clientFormat) and
      (__ \ "accessibleTo").lazyFormat[List[Accessor]](accessorListFormat) and
      (__ \ "event").formatNullable[SystemEventMeta]
    )(AnnotationMeta.apply _, unlift(AnnotationMeta.unapply _))

  implicit val permissionGrantMetaFormat: Format[PermissionGrantMeta] = (
    __.format[PermissionGrantF] and
      (__ \ "accessor").lazyFormatNullable[Accessor](Accessor.Converter.clientFormat) and
      (__ \ "targets").lazyFormat[List[MetaModel[_]]](Reads.list[MetaModel[_]](MetaModel.Converter.clientFormat), Writes.list[MetaModel[_]](MetaModel.Converter.clientFormat)) and
      (__ \ "scope").lazyFormatNullable[MetaModel[_]](MetaModel.Converter.clientFormat) and
      (__ \ "grantedBy").lazyFormatNullable[UserProfileMeta](userProfileMetaFormat)
    )(PermissionGrantMeta.apply _, unlift(PermissionGrantMeta.unapply _))

  implicit val groupMetaFormat: Format[GroupMeta] = (
      __.format[GroupF] and
      (__ \ "groups").lazyFormat[List[GroupMeta]](Reads.list[GroupMeta], Writes.list[GroupMeta]) and
      (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
      (__ \ "event").formatNullable[SystemEventMeta]
    )(GroupMeta.apply _, unlift(GroupMeta.unapply _))

  implicit val countryMetaFormat: Format[CountryMeta] = (
    __.format[CountryF] and
      (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
      (__ \ "event").formatNullable[SystemEventMeta]
    )(CountryMeta.apply _, unlift(CountryMeta.unapply _))

  implicit val repositoryMetaFormat: Format[RepositoryMeta] = (
    __.format[RepositoryF] and
    (__ \ "country").formatNullable[CountryMeta] and
    (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
    (__ \ "event").formatNullable[SystemEventMeta]
  )(RepositoryMeta.apply _, unlift(RepositoryMeta.unapply _))

  implicit val documentaryUnitMetaFormat: Format[DocumentaryUnitMeta] = (
    __.format[DocumentaryUnitF] and
    (__ \ "holder").formatNullable[RepositoryMeta] and
    (__ \ "parent").formatNullable[DocumentaryUnitMeta] and
    (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
    (__ \ "event").formatNullable[SystemEventMeta]
  )(DocumentaryUnitMeta.apply _, unlift(DocumentaryUnitMeta.unapply _))

  implicit val vocabularyMetaFormat: Format[VocabularyMeta] = (
    __.format[VocabularyF] and
    (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
    (__ \ "event").formatNullable[SystemEventMeta]
  )(VocabularyMeta.apply _, unlift(VocabularyMeta.unapply _))

  implicit val conceptMetaFormat: Format[ConceptMeta] = (
    __.format[ConceptF] and
    (__ \ "vocabulary").formatNullable[VocabularyMeta] and
    (__ \ "parent").lazyFormatNullable[ConceptMeta](conceptMetaFormat) and
    (__ \ "broaderTerms").lazyFormat[List[ConceptMeta]](Reads.list[ConceptMeta], Writes.list[ConceptMeta]) and
    (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
    (__ \ "event").formatNullable[SystemEventMeta]
  )(ConceptMeta.apply _, unlift(ConceptMeta.unapply _))

  implicit val authoritativeSetMetaFormat: Format[AuthoritativeSetMeta] = (
    __.format[AuthoritativeSetF] and
    (__ \ "accessibleTo").lazyFormat[List[Accessor]](
        accessorListFormat) and
    (__ \ "event").formatNullable[SystemEventMeta]
  )(AuthoritativeSetMeta.apply _, unlift(AuthoritativeSetMeta.unapply _))

  implicit val historicalAgentMetaFormat: Format[HistoricalAgentMeta] = (
    __.format[HistoricalAgentF] and
    (__ \ "set").formatNullable[AuthoritativeSetMeta] and
    (__ \ "accessibleTo").lazyFormat[List[Accessor]](accessorListFormat) and
    (__ \ "event").formatNullable[SystemEventMeta]
  )(HistoricalAgentMeta.apply _, unlift(HistoricalAgentMeta.unapply _))

}
