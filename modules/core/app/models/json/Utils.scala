package models.json

import models.base.AnyModel
import defines.EntityType
import models._
import play.api.libs.json.Reads

/**
 * @author Mike Bryant (http://github.com/mikesname
 */
object Utils {
  val restReadRegistry: PartialFunction[EntityType.Value, Reads[AnyModel]] = {
    case EntityType.Repository => Repository.RepositoryResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.Country => Country.CountryResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.DocumentaryUnit => DocumentaryUnit.DocumentaryUnitResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.Vocabulary => Vocabulary.VocabularyResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.Concept => Concept.ConceptResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.HistoricalAgent => HistoricalAgent.HistoricalAgentResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.AuthoritativeSet => AuthoritativeSet.AuthoritativeSetResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.SystemEvent => SystemEvent.SystemEventResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.Group => Group.GroupResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.UserProfile => UserProfile.UserProfileResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.Link => Link.LinkResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.Annotation => Annotation.AnnotationResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.PermissionGrant => PermissionGrant.PermissionGrantResource.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.ContentType => ContentType.Converter.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.AccessPoint => AccessPoint.Converter.restReads.asInstanceOf[Reads[AnyModel]]
    case EntityType.VirtualUnit => VirtualUnit.VirtualUnitResource.restReads.asInstanceOf[Reads[AnyModel]]
  }
}
