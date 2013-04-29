package models

import models.base.{NamedEntity, LinkableEntity, AnnotatableEntity, Formable}

import defines.EntityType
import play.api.libs.json.Json
import java.util.NoSuchElementException


object AccessPointF {
  val TYPE = "type"
  val DESCRIPTION = "description"
  val TARGET = "name" // Change to something better!

  object AccessPointType extends Enumeration {
    type Type = Value
    val CreatorAccess = Value("creatorAccess")
    val PersonAccess = Value("personAccess")
    val CorporateBodyAccess = Value("corporateBodyAccess")
    val SubjectAccess = Value("subjectAccess")
    val PlaceAccess = Value("placeAccess")
    val Other = Value("otherAccess")
  }
}

case class AccessPointF(
  val id: Option[String],
  val `type`: AccessPointF.AccessPointType.Value,
  val name: String,
  val description: Option[String]
) {
  val isA = EntityType.AccessPoint
}


case class AccessPoint(val e: Entity) extends AnnotatableEntity with NamedEntity with Formable[AccessPointF] {
  import json.AccessPointFormat._
  lazy val formable: AccessPointF = Json.toJson(e).as[AccessPointF]
  lazy val formableOpt: Option[AccessPointF] = Json.toJson(e).asOpt[AccessPointF]

  lazy val targetName = e.stringProperty(AccessPointF.TARGET)

  /**
   * Get the access point type from this unparsed data.
   */
  lazy val `type`: AccessPointF.AccessPointType.Value = e.stringProperty(AccessPointF.TYPE).map { t =>
    try {
      AccessPointF.AccessPointType.withName(t)
    } catch {
      case e: NoSuchElementException => AccessPointF.AccessPointType.Other
    }
  }.getOrElse(AccessPointF.AccessPointType.Other)

  /**
   * Given a set of links, see if we can find one with this access point
   * as a body.
   * @param links
   * @return
   */
  def linkFor(links: List[Link]): Option[Link] = links.find(_.bodies.exists(body => body.id == id))

  /**
   * Given an item and a set of links, see if we can resolve the
   * opposing target item.
   * @param item
   * @param links
   * @return
   */
  def target(item: LinkableEntity, links: List[Link]): Option[(Link,LinkableEntity)] = linkFor(links).flatMap { link =>
    link.opposingTarget(item).map { target =>
      (link, target)
    }
  }
}

