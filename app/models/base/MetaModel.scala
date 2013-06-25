package models.base

import play.api.libs.json._
import defines.EntityType
import play.api.i18n.Lang
import models.json.{ClientConvertable, RestReadable}
import models._
import play.api.data.validation.ValidationError
import models.Group
import models.HistoricalAgent
import models.DocumentaryUnit
import play.api.data.validation.ValidationError
import models.Repository

trait Model {
  val id: Option[String]
  val isA: EntityType.Value
}

object MetaModel {
  implicit object Converter extends RestReadable[MetaModel[_]] with ClientConvertable[MetaModel[_]] {
    implicit val restReads = new Reads[MetaModel[_]] {
      def reads(json: JsValue): JsResult[MetaModel[_]] = {
        // Sniff the type...
        (json \ "type").as[EntityType.Value](defines.EnumUtils.enumReads(EntityType)) match {
          case EntityType.Repository => json.validate[RepositoryMeta](RepositoryMeta.Converter.restReads)
          case EntityType.Vocabulary => json.validate[VocabularyMeta](VocabularyMeta.Converter.restReads)
          case EntityType.Concept => json.validate[ConceptMeta](ConceptMeta.Converter.restReads)
          case EntityType.DocumentaryUnit => json.validate[DocumentaryUnitMeta](DocumentaryUnitMeta.Converter.restReads)
          case EntityType.HistoricalAgent => json.validate[HistoricalAgentMeta](HistoricalAgentMeta.Converter.restReads)
          case EntityType.SystemEvent => json.validate[SystemEventMeta](SystemEventMeta.Converter.restReads)
          case EntityType.Country => json.validate[CountryMeta](CountryMeta.Converter.restReads)
          case EntityType.Group => json.validate[GroupMeta](GroupMeta.Converter.restReads)
          case EntityType.UserProfile => json.validate[UserProfileMeta](UserProfileMeta.Converter.restReads)
          case t => JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unexpected meta-model type: " + t))
        }
      }
    }

    implicit val clientFormat = new Format[MetaModel[_]] {
      def reads(json: JsValue): JsResult[MetaModel[_]] = {
        (json \ "type").as[EntityType.Value](defines.EnumUtils.enumReads(EntityType)) match {
          case EntityType.Repository => json.validate[RepositoryMeta](RepositoryMeta.Converter.clientFormat)
          case EntityType.Vocabulary => json.validate[VocabularyMeta](VocabularyMeta.Converter.clientFormat)
          case EntityType.Concept => json.validate[ConceptMeta](ConceptMeta.Converter.clientFormat)
          case EntityType.DocumentaryUnit => json.validate[DocumentaryUnitMeta](DocumentaryUnitMeta.Converter.clientFormat)
          case EntityType.HistoricalAgent => json.validate[HistoricalAgentMeta](HistoricalAgentMeta.Converter.clientFormat)
          case EntityType.SystemEvent => json.validate[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
          case EntityType.Country => json.validate[CountryMeta](CountryMeta.Converter.clientFormat)
          case EntityType.Group => json.validate[GroupMeta](GroupMeta.Converter.clientFormat)
          case EntityType.UserProfile => json.validate[UserProfileMeta](UserProfileMeta.Converter.clientFormat)
          case t => JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unexpected meta-model for accessor type: " + t))
        }
      }
      def writes(a: MetaModel[_]): JsValue = {
        a match {
          case up: UserProfileMeta => Json.toJson(up)(UserProfileMeta.Converter.clientFormat)
          case g: GroupMeta => Json.toJson(g)(GroupMeta.Converter.clientFormat)
          case h: HistoricalAgentMeta => Json.toJson(h)(HistoricalAgentMeta.Converter.clientFormat)
          // TODO: More...
          case t => sys.error("Unexcepted type for accessor client conversion: " + t)
        }
      }
    }
  }
}

trait Named {
  def name: String
}

/**
 * Created by mike on 23/06/13.
 */
trait MetaModel[T <: Model] {
  val model: T

  // Convenience helpers
  val id = model.id.getOrElse(sys.error(s"Meta-model with no id. This shouldn't happen!: $this"))
  val isA = model.isA


  override def toString = s"TODO: $isA [$id]"
  def toStringLang(implicit lang: Lang) = s"TODO (with lang): $isA [$id]"
}

trait Hierarchical[+T] {
  val parent: Option[Hierarchical[T]]

  def ancestors: List[Hierarchical[T]]
      = (parent.map(p => p :: p.ancestors) getOrElse List.empty).distinct
}

trait Described[+T <: Model] {
  val descriptions: List[T]
  def description(id: String) = descriptions.find(_.id == Some(id))
}