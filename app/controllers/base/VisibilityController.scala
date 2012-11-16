package controllers.base

import play.api.libs.concurrent.execution.defaultContext
import models.base._
import models.base.Persistable
import defines._

trait VisibilityController[F <: Persistable, T <: AccessibleEntity with Formable[F]] extends EntityRead[T] {

  import play.api.mvc.Call
  import play.api.mvc.RequestHeader

  val visibilityAction: String => Call
  val setVisibilityAction: String => Call
  type VisibilityViewType = (Accessor, List[(String, String)], List[(String, String)], Call, Option[models.sql.User], RequestHeader) => play.api.templates.Html
  val visibilityView: VisibilityViewType

  import play.api.libs.json.Reads
  import play.api.libs.json.Reads._
  import play.api.libs.json.util._
  import play.api.libs.json._

  def visibility(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        AsyncRest {
          for {
            itemOrErr <- rest.EntityDAO(entityType, Some(userProfile)).get(id)
            users <- rest.RestHelpers.getUserList
            groups <- rest.RestHelpers.getGroupList
          } yield {
            itemOrErr.right.map { item =>
              Ok(views.html.visibility(builder(item), users, groups, setVisibilityAction(id), maybeUser, request))
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }

  def visibilityPost(id: String) = userProfileAction { implicit maybeUser =>
    implicit request =>
      maybeUser.flatMap(_.profile).map { userProfile =>
        val data = request.body.asFormUrlEncoded.getOrElse(Map()).map { case (i, s) => (i, s.toList) }
        AsyncRest {
          rest.EntityDAO(entityType, Some(userProfile)).get(id).map { itemOrErr =>
            itemOrErr.right.map { item =>
              val model = builder(item)
              AsyncRest {
                rest.VisibilityDAO(userProfile).set(model, data).map { boolOrErr =>
                  boolOrErr.right.map { bool =>
                    Redirect(showAction(id))
                  }
                }
              }
            }
          }
        }
      }.getOrElse(Unauthorized(views.html.errors.permissionDenied()))
  }
}

