package models.base

import play.api.libs.json._
import defines.EntityType
import play.api.i18n.Lang
import models.json.{Utils, ClientConvertable, RestReadable}
import models.{Entity,SystemEvent,AccessPointF,DatePeriodF}
import play.api.data.validation.ValidationError
import play.api.Logger


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
        val et = (json \ "type").as[EntityType.Value](defines.EnumUtils.enumReads(EntityType))
        Utils.restReadRegistry.get(et).map { reads =>
          json.validate(reads)
        }.getOrElse {
          JsError(
            JsPath(List(KeyPathNode("type"))),
            ValidationError(s"Unregistered AnyModel type for REST: $et (registered: ${Utils.restReadRegistry.keySet}"))
        }
      }
    }

    implicit val clientFormat: Format[AnyModel] = new Format[AnyModel] {
      def reads(json: JsValue): JsResult[AnyModel] = {
        val et = (json \ "type").as[EntityType.Value](defines.EnumUtils.enumReads(EntityType))
        Utils.clientFormatRegistry.get(et).map { format =>
          json.validate(format)
        }.getOrElse {
          JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unregistered AnyModel type for Client read: " + et))
        }
      }

      def writes(a: AnyModel): JsValue = {
        Utils.clientFormatRegistry.get(a.isA).map { format =>
          Json.toJson(a)(format)
        }.getOrElse {
          // FIXME: Throw an error here???
          Logger.logger.warn("Unregistered AnyModel type {} (Writing to Client)", a.isA)
          Json.toJson(Entity(id = a.id, `type` = a.isA, relationships = Map.empty))(models.json.entityFormat)
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
  def latestEvent: Option[SystemEvent]
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

trait DescribedMeta[TD <: Description, T <: Described[TD]] extends MetaModel[T] {
  def descriptions: List[TD] = model.descriptions
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
  val unknownProperties: List[Entity] // Unknown, unparsed data
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

  def accessPoints: List[AccessPointF]
      = descriptions.flatMap(_.accessPoints)
}

trait Temporal {
  val dates: List[DatePeriodF]
}