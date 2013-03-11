package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus, enum}
import base._

import play.api.libs.json._
import defines.EnumWriter.enumWrites

object ActorType extends Enumeration {
  type Type = Value
  val Person = Value("person")
  val Family = Value("family")
  val CorporateBody = Value("corporateBody")
}


object ActorF {

  final val DESC_REL = "describes"
  final val ADDRESS_REL = "hasAddress"

  final val UNNAMED_PLACEHOLDER = "UNNAMED Authority"

  val NAME = "name"
  val PUBLICATION_STATUS = "publicationStatus"
}

case class ActorF(
  id: Option[String],
  identifier: String,
  name: String,
  publicationStatus: Option[PublicationStatus.Value] = None,
  @Annotations.Relation(ActorF.DESC_REL) descriptions: List[ActorDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Actor

  import json.ActorFormat._
  def toJson: JsValue = Json.toJson(this)
}

object ActorDescriptionF {

  case class Details(
    datesOfExistence: Option[String] = None,
    history: Option[String] = None,
    places: Option[String] = None,
    legalStatus: Option[String] = None,
    functions: Option[String] = None,
    mandates: Option[String] = None,
    internalStructure: Option[String] = None,
    generalContext: Option[String] = None
  ) extends AttributeSet

  case class Control(
    descriptionIdentifier: Option[String] = None,
    institutionIdentifier: Option[String] = None,
    rulesAndConventions: Option[String] = None,
    status: Option[String] = None,
    levelOfDetail: Option[String] = None,
    datesCDR: Option[String] = None,
    languages: Option[List[String]] = None,
    scripts: Option[List[String]] = None,
    sources: Option[String] = None,
    maintenanceNotes: Option[String] = None
  ) extends AttributeSet

}

case class ActorDescriptionF(
  id: Option[String],
  languageCode: String,
  entityType: ActorType.Value,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  details: ActorDescriptionF.Details,
  control: ActorDescriptionF.Control
) extends Persistable {
  val isA = EntityType.ActorDescription

  import json.IsaarFormat._
  def toJson: JsValue = Json.toJson(this)
}


case class Actor(val e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with DescribedEntity
  with Formable[ActorF] {
  override def descriptions: List[ActorDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(ActorDescription(_))

  val publicationStatus = e.property(ActorF.PUBLICATION_STATUS).flatMap(enum(PublicationStatus).reads(_).asOpt)

  import json.ActorFormat._
  lazy val formable: ActorF = Json.toJson(e).as[ActorF]
}

case class ActorDescription(val e: Entity) extends Description with Formable[ActorDescriptionF] {
  lazy val item: Option[Actor] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Actor(_))

  import json.IsaarFormat._
  lazy val formable: ActorDescriptionF = Json.toJson(e).as[ActorDescriptionF]
}



