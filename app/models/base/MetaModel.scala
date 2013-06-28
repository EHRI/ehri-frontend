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

  //override def toString = s"TODO: $isA [$id]"
  def toStringLang(implicit lang: Lang): String = this match {
    case e: MetaModel[_] => e.toStringLang(Lang.defaultLang)
    case t => t.toString
  }
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
          case EntityType.Link => json.validate[LinkMeta](LinkMeta.Converter.restReads)
          case EntityType.Annotation => json.validate[AnnotationMeta](AnnotationMeta.Converter.restReads)
          case t => JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unexpected AnyModel type: " + t))
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
          case EntityType.Link => json.validate[LinkMeta](LinkMeta.Converter.clientFormat)
          case EntityType.Annotation => json.validate[AnnotationMeta](AnnotationMeta.Converter.clientFormat)
          case t => JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unexpected AnyModel for accessor type: " + t))
        }
      }
      def writes(a: AnyModel): JsValue = {
        a match {
          case v: UserProfileMeta => Json.toJson(v)(UserProfileMeta.Converter.clientFormat)
          case v: GroupMeta => Json.toJson(v)(GroupMeta.Converter.clientFormat)
          case v: HistoricalAgentMeta => Json.toJson(v)(HistoricalAgentMeta.Converter.clientFormat)
          case v: LinkMeta => Json.toJson(v)(LinkMeta.Converter.clientFormat)
          case v: DocumentaryUnitMeta => Json.toJson(v)(DocumentaryUnitMeta.Converter.clientFormat)
          case v: RepositoryMeta => Json.toJson(v)(RepositoryMeta.Converter.clientFormat)
          case v: AnnotationMeta => Json.toJson(v)(AnnotationMeta.Converter.clientFormat)
          case v: AuthoritativeSetMeta => Json.toJson(v)(AuthoritativeSetMeta.Converter.clientFormat)
          case v: ConceptMeta => Json.toJson(v)(ConceptMeta.Converter.clientFormat)
          case v: VocabularyMeta => Json.toJson(v)(VocabularyMeta.Converter.clientFormat)
          case v: SystemEventMeta => Json.toJson(v)(SystemEventMeta.Converter.clientFormat)
          case v: CountryMeta => Json.toJson(v)(CountryMeta.Converter.clientFormat)
          case t => sys.error("Unexcepted type for AnyModel client conversion: " + t)
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

  override def toStringLang(implicit lang: Lang) = {
    if (model.isInstanceOf[Described[_]]) {
      val d = model.asInstanceOf[Described[Description]]
      d.descriptions.find(_.languageCode == lang.code).orElse(d.descriptions.headOption).map(_.name).getOrElse(id)
    } else id
  }

  override def toStringAbbr(implicit lang: Lang): String = toStringLang(lang)
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
  val languageCode: String
  val accessPoints: List[AccessPointF]
}

object Described {
  final val REL = "describes"
}

trait Described[+T <: Description] extends Model {
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