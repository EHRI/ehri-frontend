package models

import models.base._

import defines.EntityType
import play.api.libs.json.{Format, JsValue, Json}
import java.util.NoSuchElementException
import models.json.{ClientConvertable, RestConvertable}


object AccessPointF {

  val TYPE = "type"
  val DESCRIPTION = "description"
  val TARGET = "name" // Change to something better!

  object AccessPointType extends Enumeration {
    type Type = Value
    val CreatorAccess = Value("creatorAccess")
    val PersonAccess = Value("personAccess")
    val FamilyAccess = Value("familyAccess")
    val CorporateBodyAccess = Value("corporateBodyAccess")
    val SubjectAccess = Value("subjectAccess")
    val PlaceAccess = Value("placeAccess")
    val Other = Value("otherAccess")

    implicit val format = defines.EnumUtils.enumFormat(this)
  }

  implicit object Converter extends RestConvertable[AccessPointF] with ClientConvertable[AccessPointF] {
    lazy val restFormat = models.json.rest.accessPointFormat
    lazy val clientFormat = models.json.client.accessPointFormat
  }
}

case class AccessPointF(
  isA: EntityType.Value = EntityType.AccessPoint,
  id: Option[String],
  accessPointType: AccessPointF.AccessPointType.Value,
  name: String,
  description: Option[String] = None
) extends Model with Persistable


case class AccessPoint(val e: Entity) extends AnnotatableEntity with NamedEntity with Formable[AccessPointF] {

  lazy val formable: AccessPointF = Json.toJson(e).as[AccessPointF](json.AccessPointFormat.restFormat)
  lazy val formableOpt: Option[AccessPointF] = Json.toJson(e).asOpt[AccessPointF](json.AccessPointFormat.restFormat)

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

