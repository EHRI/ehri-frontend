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
    EntityType.Repository -> Repository.RepositoryResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Country -> Country.CountryResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.DocumentaryUnit -> DocumentaryUnit.DocumentaryUnitResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Vocabulary -> Vocabulary.VocabularyResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Concept -> Concept.ConceptResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.HistoricalAgent -> HistoricalAgent.HistoricalAgentResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.AuthoritativeSet -> AuthoritativeSet.AuthoritativeSetResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.SystemEvent -> SystemEvent.SystemEventResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Group -> Group.GroupResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.UserProfile -> UserProfile.UserProfileResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Link -> Link.LinkResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Annotation -> Annotation.AnnotationResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.PermissionGrant -> PermissionGrant.PermissionGrantResource.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.ContentType -> ContentType.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.AccessPoint -> AccessPoint.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.VirtualUnit -> VirtualUnit.VirtualUnitResource.restReads.asInstanceOf[Reads[AnyModel]]
  )
}
