package models

/**
 * Classes representing an ISDIAH collection-holding institution
 */

import defines.{EntityType, PublicationStatus, enum}
import base._

import play.api.libs.json._
import defines.EnumWriter.enumWrites

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
  lazy val formableOpt: Option[ActorF] = Json.toJson(e).asOpt[ActorF]

  override def toString = {
    descriptions.headOption.flatMap(d => d.stringProperty(Isdiah.AUTHORIZED_FORM_OF_NAME)).getOrElse(identifier)
  }
}




