package models.base

import play.api.libs.json._
import defines.EntityType
import play.api.i18n.Lang
import models.json.{ClientConvertable, RestReadable}
import models._
import play.api.data.validation.ValidationError


trait AnyModel {
  val id: String
  val isA: EntityType.Value

  override def toString = s"TODO: $isA [$id]"
  def toStringLang(implicit lang: Lang) = s"TODO (with lang): $isA [$id]"
  def toStringAbbr(implicit lang: Lang) = s"TODO (with abbr): $isA [$id]"
}

trait Model {
  val id: Option[String]
  val isA: EntityType.Value
}

object AnyModel {
  implicit object Converter extends RestReadable[AnyModel] with ClientConvertable[AnyModel] {
    implicit val restReads: Reads[AnyModel] = new Reads[AnyModel] {
      def reads(json: JsValue): JsResult[AnyModel] = {
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

    implicit val clientFormat: Format[AnyModel] = new Format[AnyModel] {
      def reads(json: JsValue): JsResult[AnyModel] = {
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
      def writes(a: AnyModel): JsValue = {
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

object Accessible {
  final val REL = "access"
  final val EVENT_REL = "lifecycleEvent"
}

trait Accessible extends AnyModel {
  def accessors: List[Accessor]
  def latestEvent: Option[SystemEventMeta]
}

/**
 * Created by mike on 23/06/13.
 */
trait MetaModel[+T <: Model] extends AnyModel {
  val model: T

  // Convenience helpers
  val id = model.id.getOrElse(sys.error(s"Meta-model with no id. This shouldn't happen!: $this"))
  val isA = model.isA
}

trait Hierarchical[+T] extends AnyModel {
  val parent: Option[Hierarchical[T]]

  def ancestors: List[Hierarchical[T]]
      = (parent.map(p => p :: p.ancestors) getOrElse List.empty).distinct
}

object Description {
  final val ACCESS_REL = "relatesTo"
  final val UNKNOWN_PROP = "hasUnknownProperty"
}

trait Description extends Model {
  val name: String
  val accessPoints: List[AccessPointF]
}

object Described {
  final val REL = "describes"
}

trait Described[+T <: Description] {
  val descriptions: List[T]
  def description(id: String) = descriptions.find(_.id == Some(id)): Option[T]
  def primaryDescription(id: Option[String])(implicit lang: Lang): Option[T]
      = id.map(s => primaryDescription(s)).getOrElse(primaryDescription)
  def primaryDescription(implicit lang: Lang) = descriptions.headOption
  def primaryDescription(id: String)(implicit lang: Lang) = {
    // TODO:!!!
    description(id)
  }
}

trait Temporal {
  val dates: List[DatePeriodF]
}