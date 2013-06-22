package models

import models.base._
import defines.EntityType
import play.api.libs.json.{Format, Json}
import models.json.{ClientConvertable, RestConvertable}


object LinkF {

  val LINK_TYPE = "type"
  val DESCRIPTION = "description"

  object LinkType extends Enumeration {
    type Type = Value
    val Hierarchical = Value("hierarchical")
    val Associative = Value("associative")
    val Family = Value("family")
    val Temporal = Value("temporal")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  lazy implicit val linkFormat: Format[LinkF] = json.LinkFormat.restFormat

  implicit object Converter extends RestConvertable[LinkF] with ClientConvertable[LinkF] {
    lazy val restFormat = models.json.rest.linkFormat
    lazy val clientFormat = models.json.client.linkFormat
  }
}

case class LinkF(
  isA: EntityType.Value = EntityType.Link,
  id: Option[String],
  linkType: LinkF.LinkType.Type,
  description: Option[String]
) extends Persistable


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

