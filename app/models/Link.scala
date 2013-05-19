package models

import models.base._
import defines.EntityType
import play.api.libs.json.Json
import models.LinkF.LinkType


object LinkF {

  val LINK_TYPE = "type"
  val DESCRIPTION = "description"

  object LinkType extends Enumeration {
    type Type = Value
    val Hierarchical = Value("hierarchical")
    val Associative = Value("associative")
    val Family = Value("family")
    val Temporal = Value("temporal")
  }

  lazy implicit val linkFormat = json.LinkFormat.linkFormat
}

case class LinkF(
  val id: Option[String],
  val linkType: LinkType.Type,
  val description: Option[String]
) extends Persistable {
  val isA = EntityType.Link

  def toJson = Json.toJson(this)
}



object Link {
  final val LINK_REL = "hasLinkTarget"
  final val ACCESSOR_REL = "hasLinker"
  final val BODY_REL = "hasLinkBody"
}

case class Link(val e: Entity) extends AccessibleEntity
  with AnnotatableEntity
  with Formable[LinkF] {

  lazy val targets: List[LinkableEntity] = e.relations(Link.LINK_REL).flatMap(LinkableEntity.fromEntity(_))
  lazy val accessor: Option[Accessor] = e.relations(Link.ACCESSOR_REL).headOption.map(Accessor(_))
  lazy val bodies: List[AccessPoint] = e.relations(Link.BODY_REL).map(AccessPoint(_))

  lazy val formable: LinkF = Json.toJson(e).as[LinkF]
  lazy val formableOpt: Option[LinkF] = Json.toJson(e).asOpt[LinkF]

  def opposingTarget(item: LinkableEntity): Option[LinkableEntity] = targets.find(_.id != item.id)
}

