package controllers.generic

import models.AccessPointF.AccessPointType
import models._
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent}


trait AccessPoints[MT <: DescribedModel] extends Read[MT] {

  case class Target(
    id: String,
    `type`: EntityType.Value
  )

  case class LinkItem(
    accessPoint: AccessPointF,
    link: Option[LinkF],
    target: Option[Target]
  )

  case class AccessPointTypeData(
    `type`: AccessPointType.Value,
    data: Seq[LinkItem]
  )

  case class ItemAccessPoints(
    id: Option[String],
    data: Seq[AccessPointTypeData]
  )

  private implicit val accessPointFormat: Format[AccessPointF] = Json.format[AccessPointF]
  private implicit val linkFormat: Format[LinkF] = Json.format[LinkF]
  private implicit val targetWrites: Format[Target] = Json.format[Target]
  private implicit val itemWrites: Format[LinkItem] = Json.format[LinkItem]
  private implicit val accessPointTypeDataFormat: Format[AccessPointTypeData] = Json.format[AccessPointTypeData]
  private implicit val itemAccessPointsFormat: Format[ItemAccessPoints] = Json.format[ItemAccessPoints]

  /**
    * Translate an item's access points and accompanying links into a more
    * easily consumable format. We start out with a list of links belonging
    * to the item. Then, for each description we check if that link's body is
    * an access point (with, say, descriptive text.) We then sort the access
    * points for each description according to their type and return them in
    * a map along with the accompanying link data (if any.) The result looks
    * something like:
    *
    * [ {
    * "id" : "e6410af3-c45e-4649-9b5e-6753b2aa1156",
    * "data" : [ {
    *   "type" : "creator",
    *   "data" : [ ]
    * }, {
    *   "type" : "person",
    *   "data" : [ ]
    * }, {
    *   "type" : "family",
    *   "data" : [ ]
    * }, {
    *   "type" : "corporateBody",
    *   "data" : [ ]
    * }, {
    *   "type" : "subject",
    *   "data" : [ ]
    * }, {
    *   "type" : "place",
    *   "data" : [ {
    *     "accessPoint" : {
    *       "isA" : "AccessPoint",
    *       "id" : "07238843-5b75-4af0-80b6-67c17c285686",
    *       "accessPointType" : "placeAccess",
    *       "name" : "Wiener Library Archives",
    *       "description" : ""
    *     },
    *     "link" : {
    *       "isA" : "Link",
    *       "id" : "1fdb1ece-8202-496d-a01d-33cdddede00f",
    *       "linkType" : "associative",
    *       "description" : ""
    *     },
    *     "target" : {
    *       "id" : "il-002821",
    *       "type" : "Repository"
    *     }
    *   } ]
    * }, {
    *   "type" : "genre",
    *   "data" : [ ]
    * } ]
    * } ]
    *
    */
  def getAccessPointsJson(id: String)(implicit rs: Resource[MT]): Action[AnyContent] =
    OptionalUserAction.async { implicit request =>

      for (item <- userDataApi.get(id); links <- userDataApi.links[Link](id)) yield {
        val itemAccessPoints: Seq[ItemAccessPoints] = item.data.descriptions.map { desc =>
          val accessPointTypes: Seq[AccessPointTypeData] = AccessPointF.AccessPointType.values.toList.map { apt =>
            val apTypes: Seq[LinkItem] = desc.accessPoints.filter(_.accessPointType == apt).map { ap =>
              val linkOpt: Option[(Link, Model)] = ap.target(item, links)
              LinkItem(
                ap,
                linkOpt.map(_._1.data),
                linkOpt.map { case (_, m) => Target(m.id, m.isA) }
              )
            }
            AccessPointTypeData(apt, apTypes)
          }
          ItemAccessPoints(desc.id, accessPointTypes)
        }
        Ok(Json.toJson(itemAccessPoints))
      }
    }
}
