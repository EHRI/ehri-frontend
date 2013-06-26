package models.base

import models._
import defines.EntityType
import models.DocumentaryUnitDescription
import models.HistoricalAgentDescription
import models.RepositoryDescription
import play.api.libs.json.JsValue

/*
object Description {

  final val ACCESS_REL = "relatesTo"
  final val UNKNOWN_PROP = "hasUnknownProperty"
  final val NAME = "name"
  final val LANGUAGE_CODE = "languageCode"

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
  val name = e.stringProperty(Description.NAME).getOrElse(id)
	val languageCode: String = e.stringProperty(Description.LANGUAGE_CODE).getOrElse("Unknown language")



  lazy val accessPoints: List[AccessPoint] = e.relations(Description.ACCESS_REL).map(AccessPoint(_))

  def placeAccess = accessPoints.filter(ap => ap.stringProperty(AccessPointF.TYPE) == Some(AccessPointF.AccessPointType.PlaceAccess.toString))

  def unknownProperty: List[Entity] = e.relations(Description.UNKNOWN_PROP)

  override def toString = name
}*/
