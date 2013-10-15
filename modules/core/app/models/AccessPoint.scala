package models

import models.base._

import defines.EntityType
import models.json.{RestReadable, ClientConvertable, RestConvertable}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.Lang


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
    lazy val restFormat = models.json.AccessPointFormat.restFormat
    lazy val clientFormat = Json.format[AccessPointF]
  }
}

case class AccessPointF(
  isA: EntityType.Value = EntityType.AccessPoint,
  id: Option[String],
  accessPointType: AccessPointF.AccessPointType.Value,
  name: String,
  description: Option[String] = None
) extends Model with Persistable {

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
  def target(item: AnyModel, links: List[Link]): Option[(Link,AnyModel)] = linkFor(links).flatMap { link =>
    link.opposingTarget(item).map { target =>
      (link, target)
    }
  }
}

object AccessPoint {
  implicit object Converter extends RestReadable[AccessPoint] with ClientConvertable[AccessPoint] {
    val restReads = models.json.AccessPointFormat.metaReads

    // This hassle necessary because single-field case classes require special handling,
    // see: http://stackoverflow.com/a/17282296/285374
    private implicit val accessPointFormat = Json.format[AccessPointF]
    private val clr: Reads[AccessPoint] = (__.read[AccessPointF].map{l => AccessPoint(l)})
    private val clw: Writes[AccessPoint] = (__.write[AccessPointF].contramap{(l: AccessPoint) => l.model})
    val clientFormat: Format[AccessPoint] = Format(clr, clw)
  }
}


case class AccessPoint(
  model: AccessPointF,
  meta: JsObject = JsObject(Seq())
) extends AnyModel with MetaModel[AccessPointF] {

  override def toStringLang(implicit lang: Lang) = "Access Point: (" + id + ")"
}
