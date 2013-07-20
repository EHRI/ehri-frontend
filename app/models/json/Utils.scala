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
    AnyModel.registerRest(EntityType.Repository, Repository.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Country, r = Country.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.DocumentaryUnit, DocumentaryUnit.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Vocabulary, Vocabulary.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Concept, Concept.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.HistoricalAgent, HistoricalAgent.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.AuthoritativeSet, AuthoritativeSet.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.SystemEvent, SystemEvent.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Group, Group.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.UserProfile, UserProfile.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Link, Link.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.Annotation, Annotation.Converter.restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerRest(EntityType.PermissionGrant, PermissionGrant.Converter.restReads.asInstanceOf[Reads[AnyModel]])


    AnyModel.registerClient(EntityType.Repository, Repository.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Country, Country.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.DocumentaryUnit, DocumentaryUnit.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Vocabulary, Vocabulary.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Concept, Concept.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.HistoricalAgent, HistoricalAgent.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.AuthoritativeSet, AuthoritativeSet.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.SystemEvent, SystemEvent.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Group, Group.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.UserProfile, UserProfile.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Link, Link.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.Annotation, Annotation.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
    AnyModel.registerClient(EntityType.PermissionGrant, PermissionGrant.Converter.clientFormat.asInstanceOf[Format[AnyModel]])
  }


}
