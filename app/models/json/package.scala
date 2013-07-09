package models

import defines.EntityType
import play.api.libs.json._
import play.api.data.validation.ValidationError
import play.api.libs.json.JsSuccess
import play.api.libs.json.util._
import play.api.libs.functional.syntax._
import models.base.AnyModel

/**
 * User: michaelb
 */
package object json {

  def registerModels: Unit = {
    AnyModel.registerRest(EntityType.Repository, RepositoryMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Country, r = CountryMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.DocumentaryUnit, DocumentaryUnitMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Vocabulary, VocabularyMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Concept, ConceptMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.HistoricalAgent, HistoricalAgentMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.AuthoritativeSet, AuthoritativeSetMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.SystemEvent, SystemEventMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Group, GroupMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.UserProfile, UserProfileMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Link, LinkMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Annotation, AnnotationMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.PermissionGrant, PermissionGrantMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])


    AnyModel.registerClient(EntityType.Repository, RepositoryMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Country, CountryMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.DocumentaryUnit, DocumentaryUnitMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Vocabulary, VocabularyMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Concept, ConceptMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.HistoricalAgent, HistoricalAgentMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.AuthoritativeSet, AuthoritativeSetMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.SystemEvent, SystemEventMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Group, GroupMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.UserProfile, UserProfileMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Link, LinkMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Annotation, AnnotationMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.PermissionGrant, PermissionGrantMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
  }

  /**
   * Reader for the EntityType enum
   */
  implicit val entityTypeReads = defines.EnumUtils.enumReads(EntityType)
  implicit val entityTypeFormat = defines.EnumUtils.enumFormat(EntityType)

  implicit val entityWrites: Writes[Entity] = (
    (__ \ Entity.ID).write[String] and
      (__ \ Entity.TYPE).write[EntityType.Type](defines.EnumUtils.enumWrites) and
      (__ \ Entity.DATA).lazyWrite(Writes.map[JsValue]) and
      (__ \ Entity.RELATIONSHIPS).lazyWrite(Writes.map[List[Entity]])
    )(unlift(Entity.unapply))

  implicit val entityReads: Reads[Entity] = (
    (__ \ Entity.ID).read[String] and
      (__ \ Entity.TYPE).read[EntityType.Type](defines.EnumUtils.enumReads(EntityType)) and
      (__ \ Entity.DATA).lazyRead(Reads.map[JsValue]) and
      (__ \ Entity.RELATIONSHIPS).lazyRead(Reads.map[List[Entity]](Reads.list(entityReads)))
    )(Entity.apply _)

  implicit val entityFormat = Format(entityReads, entityWrites)


  /**
   * Reads combinator that checks if a value is equal to the expected value.
   */
  def equalsReads[T](t: T)(implicit r: Reads[T]): Reads[T] = Reads.filter(ValidationError("validate.error.incorrectType", t))(_ == t)
  def equalsFormat[T](t: T)(implicit r: Format[T]): Format[T] = Format(equalsReads(t), r)

  /**
   * Writes a list value as null if the list is empty. Reads as an empty list
   * if the path is null.
   * @param path
   * @param fmt
   * @tparam T
   * @return
   */
  def nullableListOf[T](path: JsPath)(implicit fmt: Format[T]): OFormat[List[T]] = {
    new OFormat[List[T]] {
      def reads(json: JsValue): JsResult[List[T]] = {
        json.validate[List[T]].fold(
          invalid = { err =>
            JsSuccess[List[T]](List.empty[T], path)
          },
          valid = { v =>
            JsSuccess[List[T]](v, path)
          }
        )
      }

      def writes(o: List[T]): JsObject
      = if (o.isEmpty) Json.obj()
      else path.write[List[T]].writes(o)
    }
  }
}
