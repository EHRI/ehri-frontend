package models

import models.base._

import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.Lang
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import backend.{Entity, BackendReadable, BackendWriteable}


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

    def exceptCreator: ValueSet = values.filterNot(_ == CreatorAccess)
  }

  import Entity.{TYPE => ETYPE,_}

  implicit val accessPointReads: Reads[AccessPointF] = (
    (__ \ ETYPE).readIfEquals(EntityType.AccessPoint) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ TYPE).readWithDefault(AccessPointType.Other) and
    // FIXME: Target should be consistent!!!
    ((__ \ DATA \ TARGET).read[String]
      orElse (__ \ DATA \ DESCRIPTION).read[String]
      orElse Reads.pure("Unknown target!")) and
    (__ \ DATA \ DESCRIPTION).readNullable[String]
  )(AccessPointF.apply _)

  implicit val accessPointWrites = new Writes[AccessPointF] {
    def writes(d: AccessPointF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          TYPE -> d.accessPointType,
          TARGET -> d.name,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit object Converter extends BackendWriteable[AccessPointF] {
    lazy val restFormat = Format(accessPointReads,accessPointWrites)
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
   */
  def linkFor(links: Seq[Link]): Option[Link] = links.find(_.bodies.exists(body => body.id == id))

  /**
   * Given a set of links, see if we can find one with this access point
   * as a body.
   */
  def linksFor(links: Seq[Link]): Seq[Link] = links.filter(_.bodies.exists(body => body.id == id))

  /**
   * Given an item and a set of links, see if we can resolve the
   * opposing target item.
   */
  def target(item: AnyModel, links: Seq[Link]): Option[(Link,AnyModel)] = linkFor(links).flatMap { link =>
    link.opposingTarget(item).map { target =>
      (link, target)
    }
  }
}

object AccessPoint {
  import Entity._
  import AccessPointF.{TYPE => ETYPE, _}
  import defines.EnumUtils.enumMapping

  implicit val metaReads: Reads[AccessPoint] = (
    __.read[AccessPointF] and
      (__ \ META).readWithDefault(Json.obj())
    )(AccessPoint.apply _)

  implicit object Converter extends BackendReadable[AccessPoint] {
    val restReads = metaReads
  }


  def linksOfType(links: Seq[Link], `type`: AccessPointF.AccessPointType.Value): Seq[Link]
      = links.filter(_.bodies.exists(body => body.accessPointType == `type`))

  val form = Form(mapping(
    ISA -> ignored(EntityType.AccessPoint),
    ID -> optional(nonEmptyText),
    ETYPE -> enumMapping(AccessPointType),
    TARGET -> nonEmptyText, // TODO: Validate this server side
    DESCRIPTION -> optional(nonEmptyText)
  )(AccessPointF.apply)(AccessPointF.unapply))
}


case class AccessPoint(
  model: AccessPointF,
  meta: JsObject = JsObject(Seq())
) extends AnyModel with MetaModel[AccessPointF] {

  override def toStringLang(implicit lang: Lang) = "Access Point: (" + id + ")"
}
