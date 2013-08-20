package controllers.base

import play.api.libs.concurrent.Execution.Implicits._
import models.base.{Described, MetaModel, Model, Description}
import defines.{PermissionType, EntityType}
import models.{UserProfile, LinkF, AccessPointF}
import play.api.libs.json.Json
import models.json.{ClientConvertable, RestReadable}
import play.api.mvc.{Request, Result, AnyContent}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait EntityAccessPoints[D <: Description, T <: Model with Described[D], MT <: MetaModel[T]] extends EntityRead[MT] {

  // NB: This doesn't work when placed within the function scope
  // should probably check if a bug has been reported.
  case class Target(id: String, `type`: EntityType.Value)
  case class LinkItem(accessPoint: AccessPointF, link: Option[LinkF], target: Option[Target])

  def manageAccessPointsAction(id: String, descriptionId: String)(f: MT => D => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      item.model.description(descriptionId).map { desc =>
        f(item)(desc)(userOpt)(request)
      }.getOrElse {
        NotFound(views.html.errors.itemNotFound(Some(descriptionId)))
      }
    }
  }

  /**
   * FIXME: Address this festering sore!
   *
   * Translate an item's access points and accompanying links into a more
   * easily consumable format. We start out with a list of links belonging
   * to the item. Then, for each description we check if that link's body is
   * an access point (with, say, descriptive text.) We then sort the access
   * points for each description according to their type and return them in
   * a map along with the accompanying link data (if any.) The result looks
   * something like:
   *
   * [ {
   *     "id" : "e6410af3-c45e-4649-9b5e-6753b2aa1156",
   *     "data" : [ {
   *       "type" : "creatorAccess",
   *       "data" : [ ]
   *     }, {
   *       "type" : "personAccess",
   *       "data" : [ ]
   *     }, {
   *       "type" : "familyAccess",
   *       "data" : [ ]
   *     }, {
   *       "type" : "corporateBodyAccess",
   *       "data" : [ ]
   *     }, {
   *       "type" : "subjectAccess",
   *       "data" : [ ]
   *     }, {
   *       "type" : "placeAccess",
   *       "data" : [ {
   *         "accessPoint" : {
   *           "isA" : "relationship",
   *           "id" : "07238843-5b75-4af0-80b6-67c17c285686",
   *           "accessPointType" : "placeAccess",
   *           "name" : "Wiener Library Archives",
   *           "description" : ""
   *         },
   *         "link" : {
   *           "isA" : "link",
   *           "id" : "1fdb1ece-8202-496d-a01d-33cdddede00f",
   *           "linkType" : "associative",
   *           "description" : ""
   *         },
   *         "target" : {
   *           "id" : "il-002821",
   *           "type" : "repository"
   *         }
   *       } ]
   *     }, {
   *       "type" : "otherAccess",
   *       "data" : [ ]
   *     } ]
   *   } ]
   *
   * @param id
   * @param rd
   * @return
   */
  def getAccessPointsJson(id: String)(
      implicit rd: RestReadable[MT]) = userProfileAction { implicit userOpt => implicit request =>
    getEntity(id, userOpt) { item =>
      AsyncRest {
        rest.LinkDAO(userOpt).getFor(id).map {

          case Right(links) => {
            implicit val accessPointFormat = AccessPointF.Converter.clientFormat
            implicit val linkFormat = LinkF.Converter.clientFormat
            implicit val targetWrites = Json.format[Target]
            implicit val itemWrites = Json.format[LinkItem]

            val list = item.model.descriptions.map { desc =>
              val accessPointTypes = AccessPointF.AccessPointType.values.toList.map { apt =>
                val apTypes = desc.accessPoints.filter(_.accessPointType == apt).map { ap =>
                  val linkOpt = links.find(_.bodies.exists(b => b.id == ap.id))
                  new LinkItem(
                    ap,
                    linkOpt.map(_.model),
                    linkOpt.flatMap(l => l.opposingTarget(item).map(t => new Target(t.id, t.isA)))
                  )
                }
                Map("type" -> Json.toJson(apt.toString), "data" -> Json.toJson(apTypes))
              }
              Map("id" -> Json.toJson(desc.id), "data" -> Json.toJson(accessPointTypes))
            }
            Right(Ok(Json.toJson(list)))
          }
          case Left(err) => Left(err)
        }
      }
    }
  }
}
