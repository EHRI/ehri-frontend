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
        NotFound(descriptionId)
      }
    }
  }


  def getAccessPointsJson(id: String)(
      implicit rd: RestReadable[MT]) = userProfileAction { implicit userOpt => implicit request =>
    getEntity(id, userOpt) { item =>
      AsyncRest {
        rest.LinkDAO(userOpt).getFor(id).map {
          case Right(links) => {
            import models.json.entityTypeFormat

            implicit val accessPointFormat = AccessPointF.Converter.clientFormat
            implicit val linkFormat = LinkF.Converter.clientFormat
            implicit val targetWrites = Json.format[Target]
            implicit val itemWrites = Json.format[LinkItem]

            val list = item.model.descriptions.map { desc =>
              val accessPointTypes = AccessPointF.AccessPointType.values.toList.map { apt =>
                val apTypes = desc.accessPoints.filter(_.accessPointType == apt).map { ap =>
                  val link = links.find(_.bodies.exists(b => b.id == ap.id))
                  new LinkItem(
                    ap,
                    link.map(_.model),
                    link.flatMap(l => l.opposingTarget(item).map(t => new Target(t.id, t.isA)))
                  )
                }
                Map("type" -> Json.toJson(apt.toString), "data" -> Json.toJson(apTypes))
              }
              Map("id" -> Json.toJson(desc.id), "data" -> Json.toJson(accessPointTypes))
            }
            println(Json.prettyPrint(Json.toJson(list)))
            Right(Ok(Json.toJson(list)))
          }
          case Left(err) => Left(err)
        }
      }
    }
  }

}
