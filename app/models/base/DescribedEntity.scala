package models.base

import models._
import defines.EntityType
import models.Group
import models.DocumentaryUnit
import models.Repository
import play.api.i18n.Lang

object DescribedEntity {
  final val DESCRIBES_REL = "describes"

  final val DESCRIPTIONS = "descriptions"

  def apply(e: Entity): DescribedEntity[_] = e.isA match {
    case EntityType.DocumentaryUnit => DocumentaryUnit(e)
    case EntityType.Repository => Repository(e)
    case EntityType.Concept => Concept(e)

    case _ => sys.error("Unknown entity type for DescribedEntity: " + e.isA.toString())
  }
}

/**
 * Object with a list of description, of type T
 * @tparam T
 */
trait DescribedEntity[T <: Description] extends AccessibleEntity {

  /**
   * Get a list of descriptions.
   * @return
   */
	def descriptions: List[T]

  /**
   * Find the description with a given id.
   * @param id
   * @return
   */
  def description(id: String): Option[T] = descriptions.find(_.id == id)

  /**
   * Fetch the 'primary' description, given an optional id, or take
   * the first available one.
   * @param id
   * @return
   */
  def primaryDescription(id: Option[String] = None): Option[T] = id match {
    case Some(did) => description(did)
    case None => descriptions.headOption
  }

  // Language-aware toString
  def toStringLang(implicit lang: Lang) = descriptions
    .find(_.languageCode==lang.code).orElse(descriptions.headOption)
      .map(_.toString).getOrElse(identifier)
}