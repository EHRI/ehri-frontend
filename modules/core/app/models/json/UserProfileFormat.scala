package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{Accessible, Accessor}
import eu.ehri.project.definitions.Ontology


object UserProfileFormat {
  import models.UserProfileF._
  import models.Entity._
  import Ontology._

  implicit val userProfileWrites: Writes[UserProfileF] = new Writes[UserProfileF] {
    def writes(d: UserProfileF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          LOCATION -> d.location,
          ABOUT -> d.about,
          LANGUAGES -> d.languages,
          IMAGE_URL -> d.imageUrl
        )
      )
    }
  }

  implicit val userProfileReads: Reads[UserProfileF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.UserProfile)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).read[String] and
      (__ \ DATA \ LOCATION).readNullable[String] and
      (__ \ DATA \ ABOUT).readNullable[String] and
      (__ \ DATA \ LANGUAGES).readNullable[List[String]].map(_.toList.flatten) and
      (__ \ DATA \ IMAGE_URL).readNullable[String]
    )(UserProfileF.apply _)

  implicit val restFormat: Format[UserProfileF] = Format(userProfileReads,userProfileWrites)

  private implicit val groupReads = GroupFormat.metaReads
  private implicit val systemEventReads = SystemEventFormat.metaReads

  implicit val metaReads: Reads[UserProfile] = (
    __.read[UserProfileF] and
    (__ \ RELATIONSHIPS \ ACCESSOR_BELONGS_TO_GROUP).lazyReadNullable[List[Group]](
      Reads.list[Group]).map(_.getOrElse(List.empty[Group])) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption)) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(UserProfile.quickApply _)
}