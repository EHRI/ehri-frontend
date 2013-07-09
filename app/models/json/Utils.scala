package models.json

import models.base.AnyModel
import defines.EntityType
import models._
import play.api.libs.json.{Format, Reads}

/**
 * Created with IntelliJ IDEA.
 * User: michaelb
 * Date: 09/07/13
 * Time: 12:38
 * To change this template use File | Settings | File Templates.
 */
object Utils {
  def registerModels: Unit = {
    AnyModel.registerRest(EntityType.Repository, RepositoryMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Country, r = CountryMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.DocumentaryUnit, DocumentaryUnitMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Vocabulary, VocabularyMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Concept, ConceptMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.HistoricalAgent, HistoricalAgentMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.AuthoritativeSet, AuthoritativeSetMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.SystemEvent, SystemEventMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Group, GroupMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.UserProfile, UserProfileMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Link, LinkMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Annotation, AnnotationMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.PermissionGrant, PermissionGrantMeta.Converter.restReads.asInstanceOf[Reads[AnyModel]])


    AnyModel.registerClient(EntityType.Repository, RepositoryMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Country, CountryMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.DocumentaryUnit, DocumentaryUnitMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Vocabulary, VocabularyMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Concept, ConceptMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.HistoricalAgent, HistoricalAgentMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.AuthoritativeSet, AuthoritativeSetMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.SystemEvent, SystemEventMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Group, GroupMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.UserProfile, UserProfileMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Link, LinkMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Annotation, AnnotationMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.PermissionGrant, PermissionGrantMeta.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
  }


}
