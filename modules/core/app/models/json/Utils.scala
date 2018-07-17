package models.json

import models.base.Model
import defines.EntityType
import models._
import play.api.libs.json.Reads

object Utils {
  val restReadRegistry: PartialFunction[EntityType.Value, Reads[Model]] = {
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
}
