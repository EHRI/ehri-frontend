package models.base

import models._
import defines.EntityType
import models.DocumentaryUnitDescription
import models.HistoricalAgentDescription
import models.RepositoryDescription

object Description {

  final val ACCESS_REL = "relatesTo"

  def apply(e: Entity): Description = e.isA match {
    case EntityType.DocumentaryUnitDescription => DocumentaryUnitDescription(e)
    case EntityType.RepositoryDescription => RepositoryDescription(e)
    case EntityType.HistoricalAgentDescription => HistoricalAgentDescription(e)
    case _ => sys.error("Unknown description type: " + e.isA.toString())
  }
}


trait Description extends WrappedEntity {
	val e: Entity

  val item: Option[AccessibleEntity]
  val name = e.stringProperty(AccessibleEntity.NAME).getOrElse(id)
	val languageCode: String = e.stringProperty("languageCode").getOrElse("Unknown language")

  lazy val accessPoints: List[AccessPoint] = e.relations(Description.ACCESS_REL).map(AccessPoint(_))

  override def toString = name
}