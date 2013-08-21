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
    EntityType.Repository -> Repository.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Country -> Country.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.DocumentaryUnit -> DocumentaryUnit.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Vocabulary -> Vocabulary.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Concept -> Concept.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.HistoricalAgent -> HistoricalAgent.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.AuthoritativeSet -> AuthoritativeSet.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.SystemEvent -> SystemEvent.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Group -> Group.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.UserProfile -> UserProfile.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Link -> Link.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.Annotation -> Annotation.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.PermissionGrant -> PermissionGrant.Converter.restReads.asInstanceOf[Reads[AnyModel]],
    EntityType.ContentType -> ContentType.Converter.restReads.asInstanceOf[Reads[AnyModel]]
  )

  val clientFormatRegistry: Map[EntityType.Value, Format[AnyModel]] = Map(
    EntityType.Repository -> Repository.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.Country -> Country.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.DocumentaryUnit -> DocumentaryUnit.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.Vocabulary -> Vocabulary.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.Concept -> Concept.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.HistoricalAgent -> HistoricalAgent.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.AuthoritativeSet -> AuthoritativeSet.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.SystemEvent -> SystemEvent.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.Group -> Group.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.UserProfile -> UserProfile.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.Link -> Link.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.Annotation -> Annotation.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.PermissionGrant -> PermissionGrant.Converter.clientFormat.asInstanceOf[Format[AnyModel]],
    EntityType.ContentType -> ContentType.Converter.clientFormat.asInstanceOf[Format[AnyModel]]
  )
}
