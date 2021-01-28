package models.base

import java.util.NoSuchElementException

import defines.{ContentTypes, EntityType}
import models._
import org.apache.commons.lang3.StringUtils
import play.api.i18n.Messages
import play.api.libs.json.{KeyPathNode, _}
import services.data.{Readable, Resource, WithId}
import utils.EnumUtils


trait Model extends WithId {
  type T <: ModelData

  def id: String = data.id.getOrElse(sys.error("Model data without ID!"))
  def isA: EntityType.Value = data.isA

  def data: T
  def meta: JsObject

  def contentType: Option[ContentTypes.Value] = try {
    Some(ContentTypes.withName(isA.toString))
  } catch {
    case e: NoSuchElementException => None
  }

  /**
    * Language-dependent version of the name. This is a fallback value.
    */
  def toStringLang(implicit messages: Messages): String = s"$isA: $id"

  /**
   * Abbreviated version of the canonical name
   */
  def toStringAbbr(implicit messages: Messages): String =
    StringUtils.abbreviate(toStringLang(messages), 80)
}

object Model {

  val readMap: PartialFunction[EntityType.Value, Reads[Model]] = {
    case EntityType.Repository => Repository.RepositoryResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.Country => Country.CountryResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.DocumentaryUnit => DocumentaryUnit.DocumentaryUnitResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.Vocabulary => Vocabulary.VocabularyResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.Concept => Concept.ConceptResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.HistoricalAgent => HistoricalAgent.HistoricalAgentResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.AuthoritativeSet => AuthoritativeSet.AuthoritativeSetResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.SystemEvent => SystemEvent.SystemEventResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.Group => Group.GroupResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.UserProfile => UserProfile.UserProfileResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.Link => Link.LinkResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.Annotation => Annotation.AnnotationResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.PermissionGrant => PermissionGrant.PermissionGrantResource.restReads.asInstanceOf[Reads[Model]]
    case EntityType.ContentType => DataContentType.Converter.restReads.asInstanceOf[Reads[Model]]
    case EntityType.AccessPoint => AccessPoint.Converter.restReads.asInstanceOf[Reads[Model]]
    case EntityType.VirtualUnit => VirtualUnit.VirtualUnitResource.restReads.asInstanceOf[Reads[Model]]
  }

  implicit object Converter extends Readable[Model] {
    implicit val restReads: Reads[Model] = Reads[Model] { json =>
      // Sniff the type...
      val et = (json \ Entity.TYPE).as(EnumUtils.enumReads(EntityType))
      readMap.lift(et).map { reads =>
        json.validate(reads)
      }.getOrElse {
        JsError(
          JsPath(List(KeyPathNode(Entity.TYPE))),
          JsonValidationError(s"Unregistered Model type: $et"))
      }
    }
  }

  /**
    * This function allows getting a dynamic Resource for an Accessor given
    * the entity type.
    */
  def resourceFor(t: EntityType.Value): Resource[Model] = new Resource[Model] {
    def entityType: EntityType.Value = t
    val restReads: Reads[Model] = Converter.restReads
  }
}

trait ModelData {
  def id: Option[String]

  def isA: EntityType.Value
}

trait Aliased extends Model {
  def allNames(implicit messages: Messages): Seq[String] = Seq(toStringLang(messages))
}

trait Named {
  def name: String
}

trait Accessible extends Model {
  /**
   * Get the set of accessors to whom this item is visible.
   */
  def accessors: Seq[Accessor]

  /**
   * Determine if a given item is private to a particular user.
   */
  def privateTo(accessor: Accessor): Boolean =
    accessors.size == 1 && accessors.head.id == accessor.id

  def latestEvent: Option[SystemEvent]
}

trait Promotable extends Accessible {
  def isPromotable: Boolean
  def promoters: Seq[UserProfile]
  def demoters: Seq[UserProfile]

  def isPromoted: Boolean = promoters.size > demoters.size
  def isPromotedBy(user: UserProfile): Boolean = promoters.exists(_.id == user.id)
  def isDemotedBy(user: UserProfile): Boolean = demoters.exists(_.id == user.id)
  def promotionScore: Int = promoters.size - demoters.size
}

trait DescribedModel extends Model {

  type T <: Described

  def descriptions: Seq[T#D] = data.descriptions

  override def toStringLang(implicit messages: Messages): String =
    data.primaryDescription(messages).orElse(descriptions.headOption).fold(id)(_.name)

  private lazy val allAccessPoints = descriptions.flatMap(_.accessPoints)

  /**
    * Links that relate to access points on this item's description(s)
    */
  def accessPointLinks(links: Seq[Link]): Seq[(Link,AccessPointF)] = for {
    link <- links.filterNot(_.bodies.isEmpty)
    accessPoint <- allAccessPoints.find(a => link.bodies.map(_.data.id).contains(a.id))
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
    link <- links.filter(_.bodies.nonEmpty)
    if link.bodies.map(_.data.id).intersect(allAccessPoints.map(_.id)).isEmpty
  } yield link

  /**
    * Links that don't relate to access points at all, such
    * as annotations that assert a relationship between two
    * items without "belonging" to either one.
    */
  def annotationLinks(links: Seq[Link]): Seq[Link] =
    links.filter(link => link.bodies.isEmpty && link.opposingTarget(this).isDefined)
}

object DescribedModel {
  val DESCRIPTIONS = "descriptions"
}

trait Holder[+T] extends Model {

  /**
   * Number of items 'below' this one.
   */
  def childCount: Option[Int] =
    meta.value.get(Entity.CHILD_COUNT).flatMap(_.asOpt[Int])

  /**
    * Whether this item has children.
    */
  def hasChildren: Boolean = childCount.exists(_ > 0)
}

trait Hierarchical[+C <: Hierarchical[C]] extends Model {

  /**
   * The parent item of this item.
   */
  def parent: Option[C]

  /**
   * List of ancestor items 'above' this one, including the parent.
   */
  def ancestors: Seq[C] =
    (parent.map(p => p +: p.ancestors) getOrElse Seq.empty[C]).distinct

  /**
   * Get the top level of the hierarchy, which may or may
   * not be the current item.
   */
  def topLevel: C = ancestors.lastOption.getOrElse(this.asInstanceOf[C])

  /**
   * Determine if an item is top level, i.e. has no parents.
   */
  def isTopLevel: Boolean = parent.isEmpty
}

trait Description extends ModelData {
  def name: String

  def languageCode: String

  def languageCode2: String = i18n.lang3to2lookup.getOrElse(languageCode, languageCode)

  def accessPoints: Seq[AccessPointF]

  def maintenanceEvents: Seq[MaintenanceEventF]

  def creationProcess: Description.CreationProcess.Value

  def unknownProperties: Seq[Entity]

  def displayText: Option[String]

  def isRightToLeft: Boolean = languageCode == "heb" || languageCode == "ara"

  def localId: Option[String] = id.flatMap(Description.localId)
}

object Description {

  val SOURCE_FILE_ID = "sourceFileId"
  val LANG_CODE = "languageCode"
  val CREATION_PROCESS = "creationProcess"
  val ACCESS_POINTS = "accessPoints"
  val UNKNOWN_DATA = "unknownData"
  val MAINTENANCE_EVENTS = "maintenanceEvents"

  val DESCRIPTION_DELIMITER = "."

  object CreationProcess extends Enumeration {
    type Type = Value
    val Import: Type = Value("IMPORT")
    val Manual: Type = Value("MANUAL")

    implicit val format: Format[CreationProcess.Value] = utils.EnumUtils.enumFormat(this)
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

  /**
   * The 'local' ID part of an individual description is the
   * section after a period.
   *
   * @param id the full id, including that described item
   * @return the local part of the id, unique to a description
   */
  def localId(id: String): Option[String] =
    if (id.contains(DESCRIPTION_DELIMITER)) Some(id.substring(id.indexOf(DESCRIPTION_DELIMITER) + 1))
    else None
}

trait Described extends ModelData {

  type D <: Description

  def descriptions: Seq[D]

  /**
   * Get a description by ID
   * @param id The description ID
   * @return A description matching that ID, optionally empty
   */
  def description(id: String): Option[D] = descriptions.find(_.id.contains(id))

  /**
   * Get a description with an optional ID, falling back on the first
   * appropriate one for the given (implicit) language code.
   * @param id The (optional) description ID
   * @param messages The current language
   * @return A description matching that ID, or the first found with that language.
   */
  def primaryDescription(id: Option[String])(implicit messages: Messages): Option[D] =
    id.fold(primaryDescription(messages))(s => primaryDescription(s))

  /**
   * Get the first description for the current language
   * @param messages The current language
   * @return The first description found with a matching language code
   */
  def primaryDescription(implicit messages: Messages): Option[D] = {
    val code3 = i18n.lang2to3lookup.getOrElse(messages.lang.language, messages.lang.language)
    descriptions.find(_.languageCode == code3).orElse(descriptions.headOption)
  }

  /**
    * List descriptions with the current language first, if possible.
    *
    * @param messages the current i18n messages
    * @return a list of descriptions
    */
  def currentLangFirstDescriptions(implicit messages: Messages): Seq[D] = {
    val code3 = i18n.lang2to3lookup.getOrElse(messages.lang.language, messages.lang.language)
    val (matchLang, others) = descriptions.partition(_.languageCode == code3)
    matchLang ++ others
  }

  /**
    * List descriptions ordered by their local identifier.
    *
    * @return a list of descriptions
    */
  def orderedDescriptions: Seq[D] = descriptions.sortBy(_.localId)

  /**
    * List ordered descriptions with a boolean indicated that
    * one matches the given local ID. The selected description
    * is the first if none match the ID.
    *
    * @param localId the selected local ID
    * @return a
    */
  def descriptionsWithSelected(localId: Option[String] = None)(implicit messages: Messages): Seq[(D, Boolean)] = {
    val descs = orderedDescriptions
    // try and find a matching local ID, if we're given one
    val didx = descs.indexWhere(d => d.localId == localId)
    val idx = if (localId.isDefined && didx > -1) didx else {
      // Try and find the first description with the current language
      val cidx = descs.indexWhere(d => d.languageCode2 == messages.lang.code)
      // Or fall back to the first description
      if (cidx > -1) cidx else 0
    }

    descs.zipWithIndex.map { case (desc, i) => desc -> (i == idx) }
  }

  /**
   * Get a description with the given ID, falling back on the first
   * appropriate one for the given (implicit) language code.
   *
   * @param id The description ID
   * @param messages The current language
   * @return A description matching that ID, or the first found with that language.
   */
  def primaryDescription(id: String)(implicit messages: Messages): Option[D] =
    description(id).orElse(primaryDescription(messages))

  def accessPoints: Seq[AccessPointF] =
    descriptions.flatMap(_.accessPoints)

  def maintenanceEvents: Seq[MaintenanceEventF] =
    descriptions.flatMap(_.maintenanceEvents).distinct.sortBy(_.date)
}

trait Temporal {
  def dates: Seq[DatePeriodF]
  def dateRange: String = dates.map(_.years).mkString(", ")
}
