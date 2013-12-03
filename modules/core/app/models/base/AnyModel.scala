package models.base

import play.api.libs.json._
import defines.EntityType
import play.api.i18n.Lang
import models.json.{Utils, ClientConvertable, RestReadable}
import models._
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.json.KeyPathNode


trait AnyModel {
  val id: String
  val isA: EntityType.Value

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
        Utils.restReadRegistry.get(et).map {
          reads =>
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
        Utils.clientFormatRegistry.get(et).map {
          format =>
            json.validate(format)
        }.getOrElse {
          JsError(JsPath(List(KeyPathNode("type"))), ValidationError("Unregistered AnyModel type for Client read: " + et))
        }
      }

      def writes(a: AnyModel): JsValue = {
        Utils.clientFormatRegistry.get(a.isA).map {
          format =>
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

trait Accessible extends AnyModel {
  def accessors: List[Accessor]

  def latestEvent: Option[SystemEvent]
}

/**
 * Created by mike on 23/06/13.
 */
trait MetaModel[+T <: Model] extends AnyModel {
  val model: T
  val meta: JsObject

  // Convenience helpers
  val id = model.id.getOrElse(sys.error(s"Meta-model with no id. This shouldn't happen!: $this"))
  val isA = model.isA

  override def toStringLang(implicit lang: Lang) = model match {
    case d: Described[Description] =>
      d.descriptions.find(_.languageCode == lang.code).orElse(d.descriptions.headOption).map(_.name).getOrElse(id)
    case _ => id
  }

  override def toStringAbbr(implicit lang: Lang): String = toStringLang(lang)
}

trait DescribedMeta[+TD <: Description, +T <: Described[TD]] extends MetaModel[T] {
  def descriptions: List[TD] = model.descriptions

  private lazy val allAccessPoints = descriptions.flatMap(_.accessPoints)

  /**
   * Links that relate to access points on this item's description(s)
   */
  def accessPointLinks(links: Seq[Link]): Seq[Link]
  = links.filterNot(link => link.bodies.map(_.id).intersect(allAccessPoints.map(_.id)).isEmpty)


  /**
   * Links that point to this item from other item's access points.
   */
  def externalLinks(links: Seq[Link]): Seq[Link]
  = links.filter(link =>
    !link.bodies.isEmpty
      && link.bodies.map(_.id).intersect(allAccessPoints.map(_.id)).isEmpty)

  /**
   * Links that don't relate to access points.
   */
  def annotationLinks(links: Seq[Link]): Seq[Link]
  = links.filter(link => link.bodies.isEmpty)
}

trait Holder[+T] extends AnyModel {
  self: MetaModel[_] =>

  /**
   * Convenience cache of items 'below' this one. Not to
   * be relied on since it's just a volatile cache value.
   */
  def childCount: Option[Int]
  = meta.value.get(Entity.CHILD_COUNT).flatMap(_.asOpt[Int])
}

trait Hierarchical[+T] extends AnyModel {
  self: MetaModel[_] =>

  /**
   * The parent item of this item.
   */
  val parent: Option[Hierarchical[T]]

  /**
   * List of ancestor items 'above' this one, including the parent.
   */
  def ancestors: List[Hierarchical[T]]
  = (parent.map(p => p :: p.ancestors) getOrElse List.empty).distinct
}

trait Description extends Model {
  val name: String
  val languageCode: String
  val accessPoints: List[AccessPointF]
  val unknownProperties: List[Entity] // Unknown, unparsed data
}

object Description {
  /**
   * Somewhat gnarly function to get the first value from
   * a set of descriptions that is available, along with an
   * indication of it
   * @param prim The primary description
   * @param descriptions A set of 'alternate' descriptions
   * @param f A function to extract an optional string from a description
   * @tparam T The description type
   * @return A tuple of the value and whether it was found in the primary description
   */
  def firstValue[T](prim: T, descriptions: Seq[T], f: T => Option[String]): (Option[String], Boolean) = {
    if (f(prim).isDefined) (f(prim), true)
    else descriptions.find(d => f(d).isDefined).map { backup =>
      (f(backup), false)
    }.getOrElse((None, false))
  }

  /**
   * Helper for iterating over each description with a list of the other
   * descriptions that are also available.
   * @param descriptions The full list of descriptions
   * @tparam T A description type
   * @return A sequence of each element and its alterates
   */
  def iterateWithAlternates[T](descriptions: Seq[T]): Iterable[(T, Seq[T])] = for {
    i <- descriptions.indices
    elem = descriptions(i)
    before = descriptions.take(i)
    after = descriptions.drop(i + 1)
  } yield (elem, before ++ after)
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