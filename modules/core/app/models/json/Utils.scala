package models.json

import models.base.AnyModel
import defines.EntityType
import models._
import play.api.libs.json.{Format, Reads}

/**
 * @author Mike Bryant (http://github.com/mikesname
 */
object Utils {
  val restReadRegistry: Map[EntityType.Value, Reads[AnyModel]] = Map(
    EntityType.Repository -> Repository.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Country -> Country.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.DocumentaryUnit -> DocumentaryUnit.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Vocabulary -> Vocabulary.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Concept -> Concept.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.HistoricalAgent -> HistoricalAgent.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.AuthoritativeSet -> AuthoritativeSet.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.SystemEvent -> SystemEvent.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Group -> Group.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.UserProfile -> UserProfile.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Link -> Link.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Annotation -> Annotation.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.PermissionGrant -> PermissionGrant.Resource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.ContentType -> ContentType.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.AccessPoint -> AccessPoint.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.VirtualUnit -> VirtualUnit.Resource.restReads.asInstanceOf[Reads[AnyModel]]
  )
}
