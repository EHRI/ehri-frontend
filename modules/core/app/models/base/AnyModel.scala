package models.base

import play.api.libs.json._
import defines.{ContentTypes, EntityType}
import play.api.i18n.Lang
import models.json.Utils
import models._
import play.api.data.validation.ValidationError
import play.api.libs.json.KeyPathNode
import scala.collection.SortedMap
import java.util.NoSuchElementException
import backend.{Entity, BackendReadable, BackendResource}


trait AnyModel extends backend.WithId {
  def id: String

  def isA: EntityType.Value

  def contentType: Option[ContentTypes.Value] = try {
    Some(ContentTypes.withName(isA.toString))
  } catch {
    case e: NoSuchElementException => None
  }

  /**
   * Language-dependent version of the name
   */
  def toStringLang(implicit lang: Lang): String = this match {
    case e: MetaModel[_] => e.toStringLang(lang)
    case t => t.toString
  }

  /**
   * Abbreviated version of the canonical name
   */
  def toStringAbbr(implicit lang: Lang) = toStringLang(lang)
}

trait Model {
  def id: Option[String]

  def isA: EntityType.Value
}

trait Aliased extends AnyModel {
  def allNames(implicit lang: Lang): Seq[String] = Seq(toStringLang(lang))
}

object AnyModel {

  implicit object Converter extends BackendReadable[AnyModel] {
    implicit val restReads: Reads[AnyModel] = new Reads[AnyModel] {
      def reads(json: JsValue): JsResult[AnyModel] = {
        // Sniff the type...
        val et = (json \ Entity.TYPE).as(defines.EnumUtils.enumReads(EntityType))
        Utils.restReadRegistry.get(et).map { reads =>
          json.validate(reads)
        }.getOrElse {
          JsError(
            JsPath(List(KeyPathNode(Entity.TYPE))),
            ValidationError(s"Unregistered AnyModel type for REST: $et (registered: ${Utils.restReadRegistry.keySet}"))
        }
      }
    }
  }

  /**
   * This function allows getting a dynamic Resource for an Accessor given
   * the entity type.
   */
  def resourceFor(t: EntityType.Value): BackendResource[AnyModel] = new BackendResource[AnyModel] {
    def entityType: EntityType.Value = t
    val restReads = Converter.restReads
  }
}

trait Named {
  def name: String
}

trait Accessible extends AnyModel {
  def accessors: Seq[Accessor]

  def latestEvent: Option[SystemEvent]
}

trait Promotable extends Accessible {
  def isPromotable: Boolean
  def promoters: Seq[UserProfile]
  def demoters: Seq[UserProfile]

  def isPromoted: Boolean = promoters.size > demoters.size
  def isPromotedBy(user: UserProfile): Boolean = promoters.exists(_.id == user.id)
  def isDemotedBy(user: UserProfile): Boolean = demoters.exists(_.id == user.id)
  def promotionScore = promoters.size - demoters.size
}

trait MetaModel[+T <: Model] extends AnyModel {
  def model: T
  def meta: JsObject

  // Convenience helpers
  def id = model.id.getOrElse(sys.error(s"Meta-model with no id. This shouldn't happen!: $this"))

  def isA = model.isA

  override def toStringLang(implicit lang: Lang) = model match {
    case d: Described[Description] =>
      d.primaryDescription(lang).orElse(d.descriptions.headOption).fold(id)(_.name)
    case _ => id
  }

  override def toStringAbbr(implicit lang: Lang): String = toStringLang(lang)
}

trait WithDescriptions[+T <: Description] extends AnyModel {
  def descriptions: Seq[T]

  private lazy val allAccessPoints = descriptions.flatMap(_.accessPoints)

  /**
   * Links that relate to access points on this item's description(s)
   */
  def accessPointLinks(links: Seq[Link]): Seq[(Link,AccessPointF)] = for {
    link <- links.filterNot(_.bodies.isEmpty)
    accessPoint <- allAccessPoints.find(a => link.bodies.map(_.id).contains(a.id))
  } yield (link, accessPoint)

  /**
   * Links that related to access points, ordered by access point type.
   */
  def accessPointLinksByType(links: Seq[Link]): Map[AccessPointF.AccessPointType.Value, Seq[(Link, AccessPointF)]] =
    accessPointLinks(links).groupBy(_._2.accessPointType)

  /**
   * Links that point to this item from other item's access points.
   */
  def externalLinks(links: Seq[Link]): Seq[Link] = for {
    link <- links.filterNot(_.bodies.isEmpty)
      if link.bodies.map(_.id).intersect(allAccessPoints.map(_.id)).isEmpty
  } yield link

  /**
   * Links that don't relate to access points.
   */
  def annotationLinks(links: Seq[Link]): Seq[Link] =
    links.filter(link => link.bodies.isEmpty)
}

trait DescribedMeta[+TD <: Description, +T <: Described[TD]] extends MetaModel[T] with WithDescriptions[TD] {
  def descriptions: Seq[TD] = model.descriptions
}

trait Holder[+T] extends AnyModel {
  self: MetaModel[_] =>

  /**
   * Convenience cache of items 'below' this one. Not to
   * be relied on since it's just a volatile cache value.
   */
  def childCount: Option[Int] =
    meta.value.get(Entity.CHILD_COUNT).flatMap(_.asOpt[Int])
}

trait Hierarchical[+T <: Hierarchical[T]] extends AnyModel {
  self: MetaModel[_] =>

  /**
   * The parent item of this item.
   */
  def parent: Option[T]

  /**
   * List of ancestor items 'above' this one, including the parent.
   */
  def ancestors: Seq[T] =
    (parent.map(p => p +: p.ancestors) getOrElse Seq.empty).distinct

  /**
   * Get the top level of the hierarchy, which may or may
   * not be the current item.
   */
  def topLevel: T = ancestors.lastOption.getOrElse(this.asInstanceOf[T])

  /**
   * Determine if an item is top level, i.e. has no parents.
   */
  def isTopLevel: Boolean = parent.isEmpty
}

trait Description extends Model {
  def name: String

  def languageCode: String

  def accessPoints: Seq[AccessPointF]

  def creationProcess: Description.CreationProcess.Value

  def unknownProperties: Seq[Entity]

  def displayText: Option[String]

  // Unknown, unparsed data
  def toSeq: Seq[(String, Option[String])]

  def toMap: SortedMap[String, Option[String]] =
    scala.collection.immutable.TreeMap(toSeq: _*)

  def isRightToLeft = languageCode == "heb" || languageCode == "ara"

  def localId: Option[String] =
    id.map(did => did.substring(did.indexOf(Description.DESCRIPTION_DELIMITER) + 1))
}

object Description {

  val LANG_CODE = "languageCode"
  val CREATION_PROCESS = "creationProcess"
  val ACCESS_POINTS = "accessPoints"
  val UNKNOWN_DATA = "unknownData"
  val MAINTENANCE_EVENTS = "maintenanceEvents"

  val DESCRIPTION_DELIMITER = "."

  object CreationProcess extends Enumeration {
    type Type = Value
    val Import = Value("IMPORT")
    val Manual = Value("MANUAL")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

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
  def descriptions: Seq[T]

  /**
   * Get a description by ID
   * @param id The description ID
   * @return A description matching that ID, optionally empty
   */
  def description(id: String): Option[T] = descriptions.find(_.id == Some(id))

  /**
   * Get a description with an optional ID, falling back on the first
   * appropriate one for the given (implicit) language code.
   * @param id The (optional) description ID
   * @param lang The current language
   * @return A description matching that ID, or the first found with that language.
   */
  def primaryDescription(id: Option[String])(implicit lang: Lang): Option[T] =
    id.fold(primaryDescription(lang))(s => primaryDescription(s))

  /**
   * Get the first description for the current language
   * @param lang The current language
   * @return The first description found with a matching language code
   */
  def primaryDescription(implicit lang: Lang): Option[T] = {
    val code3 = utils.i18n.lang2to3lookup.getOrElse(lang.language, lang.language)
    descriptions.find(_.languageCode == code3).orElse(descriptions.headOption)
  }

  /**
   * Get a description with the given ID, falling back on the first
   * appropriate one for the given (implicit) language code.
   *
   * @param id The description ID
   * @param lang The current language
   * @return A description matching that ID, or the first found with that language.
   */
  def primaryDescription(id: String)(implicit lang: Lang): Option[T] =
    description(id).orElse(primaryDescription(lang))

  def accessPoints: Seq[AccessPointF] =
    descriptions.flatMap(_.accessPoints)
}

trait Temporal {
  def dates: Seq[DatePeriodF]
  def dateRange: String = dates.map(_.years).mkString(", ")
}